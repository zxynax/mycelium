package com.cdrware.mycelium.agent;

import com.cdrware.mycelium.common.crypto.Ed25519;
import com.cdrware.mycelium.common.crypto.HmacToken;
import com.cdrware.mycelium.common.fs.DeployFs;
import com.cdrware.mycelium.common.json.Json;
import com.cdrware.mycelium.common.manifest.Manifest;
import com.cdrware.mycelium.common.manifest.ManifestCrypto;
import com.cdrware.mycelium.common.manifest.ManifestIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;

/**
 * Node agent: poll control plane for group manifest, hardlink-stage, fetch, flip, report status.
 */
public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Args a = Args.parse(args);
        if (a.help) {
            System.out.print(Args.usage());
            return;
        }
        Path dataDir = Path.of(a.dataDir).toAbsolutePath().normalize();
        Files.createDirectories(dataDir);
        Path statePath = dataDir.resolve("state.json");
        AgentState state = AgentState.load(statePath);
        state.setNodeId(a.nodeId);
        state.setGroupId(a.groupId);

        PublicKey pub = Ed25519.decodePublic(Files.readString(Path.of(a.publicKey)).trim());
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        while (true) {
            try {
                tick(a, dataDir, statePath, state, pub, http);
            } catch (Exception e) {
                log.warn("tick failed: {}", e.toString());
                state.setStatus("error");
                state.setDetail(e.toString());
                state.save(statePath);
                report(a, state, http);
            }
            if (!a.loop) {
                break;
            }
            Thread.sleep(a.pollSeconds * 1000L);
        }
    }

    private static void tick(
            Args a,
            Path dataDir,
            Path statePath,
            AgentState state,
            PublicKey pub,
            HttpClient http
    ) throws Exception {
        Manifest remote = fetchManifest(a, http);
        if (!ManifestCrypto.verify(remote, pub)) {
            throw new SecurityException("remote manifest signature invalid");
        }
        if (remote.getVersion() != null && remote.getVersion().equals(state.getVersion())
                && "applied".equals(state.getStatus())) {
            state.setStatus("applied");
            state.setDetail("up to date " + state.getVersion());
            state.save(statePath);
            report(a, state, http);
            return;
        }

        state.setStatus("staging");
        state.setDetail("hardlink stage " + remote.getVersion());
        state.save(statePath);
        HardlinkStager.Stats stats = HardlinkStager.stage(dataDir, remote);
        log.info("staged version={} linked={} copied={} missing={}",
                remote.getVersion(), stats.hardlinked(), stats.copied(), stats.missing());

        state.setStatus("fetching");
        state.save(statePath);
        Path neu = dataDir.resolve("new");
        boolean usedLt = false;
        if (a.torrentPath != null && Files.isRegularFile(Path.of(a.torrentPath))) {
            usedLt = LibtorrentFetcher.tryDownload(
                    Path.of(a.torrentPath), dataDir, a.fetchTimeoutMs);
        }
        if (!usedLt) {
            new WebSeedFetcher().fetchMissing(neu, remote);
        }

        state.setStatus("flipping");
        state.save(statePath);
        DeployFs.flip(dataDir);

        state.setVersion(remote.getVersion());
        state.setInfohash(remote.getInfohash());
        state.setStatus("applied");
        state.setDetail("applied " + remote.getVersion()
                + (usedLt ? " via jlibtorrent" : " via webseed"));
        state.save(statePath);
        report(a, state, http);
        log.info("applied {}", remote.getVersion());
    }

    private static Manifest fetchManifest(Args a, HttpClient http) throws Exception {
        if (a.manifestUrl != null) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(a.manifestUrl))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("manifest HTTP " + resp.statusCode());
            }
            return Json.mapper().readValue(resp.body(), Manifest.class);
        }
        if (a.manifestFile != null) {
            return ManifestIO.read(Path.of(a.manifestFile));
        }
        // Control plane: GET /api/groups/{id}/manifest
        String path = "/api/groups/" + a.groupId + "/manifest";
        String url = a.controlUrl.replaceAll("/$", "") + path;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).GET();
        if (a.apiToken != null) {
            // HMAC over "METHOD path" (not absolute URL) — matches control auth
            b.header("X-Mycelium-Token", HmacToken.sign(a.apiToken, "GET " + path));
        }
        HttpResponse<byte[]> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("control manifest HTTP " + resp.statusCode());
        }
        return Json.mapper().readValue(resp.body(), Manifest.class);
    }

    private static void report(Args a, AgentState state, HttpClient http) {
        if (a.controlUrl == null || a.apiToken == null) {
            return;
        }
        try {
            String path = "/api/nodes/" + a.nodeId + "/status";
            String url = a.controlUrl.replaceAll("/$", "") + path;
            byte[] body = Json.mapper().writeValueAsBytes(state);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("X-Mycelium-Token", HmacToken.sign(a.apiToken, "POST " + path))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("status report failed: {}", e.toString());
        }
    }

    static final class Args {
        String dataDir = "data";
        String nodeId = "node-1";
        String groupId = "default";
        String publicKey;
        String controlUrl;
        String apiToken;
        String manifestUrl;
        String manifestFile;
        String torrentPath;
        long fetchTimeoutMs = 600_000;
        int pollSeconds = 30;
        boolean loop = true;
        boolean help;

        static String usage() {
            return """
                    mycelium-agent --public-key PATH --data-dir DIR --node-id ID --group-id ID \\
                      [--control-url URL --api-token SECRET] \\
                      [--manifest-url URL | --manifest-file PATH] \\
                      [--torrent PATH] [--once] [--poll-seconds N]
                    """;
        }

        static Args parse(String[] argv) {
            Args a = new Args();
            for (int i = 0; i < argv.length; i++) {
                switch (argv[i]) {
                    case "--data-dir" -> a.dataDir = argv[++i];
                    case "--node-id" -> a.nodeId = argv[++i];
                    case "--group-id" -> a.groupId = argv[++i];
                    case "--public-key" -> a.publicKey = argv[++i];
                    case "--control-url" -> a.controlUrl = argv[++i];
                    case "--api-token" -> a.apiToken = argv[++i];
                    case "--manifest-url" -> a.manifestUrl = argv[++i];
                    case "--manifest-file" -> a.manifestFile = argv[++i];
                    case "--torrent" -> a.torrentPath = argv[++i];
                    case "--fetch-timeout-ms" -> a.fetchTimeoutMs = Long.parseLong(argv[++i]);
                    case "--poll-seconds" -> a.pollSeconds = Integer.parseInt(argv[++i]);
                    case "--once" -> a.loop = false;
                    case "--help", "-h" -> a.help = true;
                    default -> throw new IllegalArgumentException("unknown: " + argv[i]);
                }
            }
            if (!a.help && a.publicKey == null) {
                throw new IllegalArgumentException("--public-key required\n" + usage());
            }
            return a;
        }
    }
}
