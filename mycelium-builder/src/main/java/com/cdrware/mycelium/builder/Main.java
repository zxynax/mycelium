package com.cdrware.mycelium.builder;

import com.cdrware.mycelium.common.crypto.Ed25519;
import com.cdrware.mycelium.common.fs.DeployFs;
import com.cdrware.mycelium.common.hash.Digests;
import com.cdrware.mycelium.common.manifest.Manifest;
import com.cdrware.mycelium.common.manifest.ManifestCrypto;
import com.cdrware.mycelium.common.manifest.ManifestFile;
import com.cdrware.mycelium.common.manifest.ManifestIO;
import com.cdrware.mycelium.common.torrent.TorrentBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CLI: artifact tree → SHA-256 inventory, private .torrent, signed manifest.
 *
 * <pre>
 * mycelium-builder --input ./dist --out ./release --version 1.2.3 \
 *   --group prod --webseed https://cdn.example/releases/1.2.3/ \
 *   --signing-key path/to/ed25519.priv
 * </pre>
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        Args a = Args.parse(args);
        if (a.help) {
            System.out.print(Args.usage());
            return;
        }
        if ("gen-keys".equals(a.mode)) {
            KeyPair kp = Ed25519.generate();
            Path dir = Path.of(a.outDir);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("ed25519.pub"), Ed25519.encodePublic(kp.getPublic()) + "\n");
            Files.writeString(dir.resolve("ed25519.priv"), Ed25519.encodePrivate(kp.getPrivate()) + "\n");
            System.out.println("Wrote " + dir.resolve("ed25519.pub") + " and ed25519.priv");
            return;
        }

        Path input = Path.of(a.input).toAbsolutePath().normalize();
        Path out = Path.of(a.outDir).toAbsolutePath().normalize();
        Files.createDirectories(out);

        List<Path> relFiles = DeployFs.listRelativeFiles(input);
        List<ManifestFile> inventory = new ArrayList<>();
        for (Path rel : relFiles) {
            Path abs = input.resolve(rel);
            inventory.add(new ManifestFile(
                    rel.toString().replace('\\', '/'),
                    Digests.sha256Hex(abs),
                    Files.size(abs)));
        }

        String torrentName = "new"; // matches agent staging directory name for jlibtorrent
        TorrentBuilder.Result torrent = TorrentBuilder.build(
                input, relFiles, torrentName, a.announce, a.webseeds, TorrentBuilder.DEFAULT_PIECE_LENGTH);
        Path torrentPath = out.resolve(a.version + ".torrent");
        Files.write(torrentPath, torrent.torrentBytes());

        Manifest manifest = new Manifest();
        manifest.setVersion(a.version);
        manifest.setGroupIds(a.groups);
        manifest.setInfohash(torrent.infohashHex());
        manifest.setTorrentName(torrentName);
        manifest.setFiles(inventory);
        manifest.setWebseeds(a.webseeds);
        manifest.setAnnounce(a.announce);
        manifest.setCreatedAt(Instant.now());

        PrivateKey priv = Ed25519.decodePrivate(Files.readString(Path.of(a.signingKey)).trim());
        ManifestCrypto.sign(manifest, priv);
        Path manifestPath = out.resolve(a.version + ".manifest.json");
        ManifestIO.write(manifestPath, manifest);

        System.out.println("version=" + a.version);
        System.out.println("files=" + inventory.size());
        System.out.println("infohash=" + torrent.infohashHex());
        System.out.println("torrent=" + torrentPath);
        System.out.println("manifest=" + manifestPath);
    }

    static final class Args {
        String mode = "build";
        String input;
        String outDir = "release";
        String version;
        List<String> groups = new ArrayList<>();
        List<String> webseeds = new ArrayList<>();
        List<String> announce = new ArrayList<>();
        String signingKey;
        boolean help;

        static String usage() {
            return """
                    Usage:
                      mycelium-builder gen-keys --out ./keys
                      mycelium-builder --input DIR --out DIR --version VER --group ID \\
                        --webseed URL [--announce URL] --signing-key PATH
                    """;
        }

        static Args parse(String[] argv) {
            Args a = new Args();
            for (int i = 0; i < argv.length; i++) {
                String s = argv[i];
                switch (s) {
                    case "gen-keys" -> a.mode = "gen-keys";
                    case "--input" -> a.input = argv[++i];
                    case "--out" -> a.outDir = argv[++i];
                    case "--version" -> a.version = argv[++i];
                    case "--group" -> a.groups.add(argv[++i]);
                    case "--webseed" -> a.webseeds.add(argv[++i]);
                    case "--announce" -> a.announce.add(argv[++i]);
                    case "--signing-key" -> a.signingKey = argv[++i];
                    case "--help", "-h" -> a.help = true;
                    default -> throw new IllegalArgumentException("unknown arg: " + s);
                }
            }
            if (a.help || "gen-keys".equals(a.mode)) {
                return a;
            }
            if (a.input == null || a.version == null || a.signingKey == null) {
                throw new IllegalArgumentException("required: --input --version --signing-key\n" + usage());
            }
            if (a.groups.isEmpty()) {
                a.groups = List.of("default");
            }
            return a;
        }
    }
}
