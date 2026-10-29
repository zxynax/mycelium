package com.cdrware.mycelium.agent;

import com.cdrware.mycelium.common.hash.Digests;
import com.cdrware.mycelium.common.manifest.Manifest;
import com.cdrware.mycelium.common.manifest.ManifestFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Cold-start / gap fill via HTTPS web seeds (BEP-19 style URL + relative path).
 * Used when jlibtorrent is unavailable or as a deterministic fallback.
 */
public final class WebSeedFetcher {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public void fetchMissing(Path newDir, Manifest manifest) throws Exception {
        if (manifest.getWebseeds() == null || manifest.getWebseeds().isEmpty()) {
            throw new IllegalStateException("no webseeds in manifest");
        }
        for (ManifestFile f : manifest.getFiles()) {
            Path dest = newDir.resolve(f.getPath());
            if (Files.isRegularFile(dest) && Files.size(dest) == f.getSize()) {
                String hash = Digests.sha256Hex(dest);
                if (hash.equalsIgnoreCase(f.getSha256())) {
                    continue;
                }
            }
            download(f, dest, manifest);
        }
    }

    private void download(ManifestFile f, Path dest, Manifest manifest) throws Exception {
        Exception last = null;
        for (String seed : manifest.getWebseeds()) {
            String base = seed.endsWith("/") ? seed : seed + "/";
            URI uri = URI.create(base + f.getPath().replace('\\', '/'));
            try {
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofMinutes(10))
                        .GET()
                        .build();
                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() / 100 != 2) {
                    last = new IllegalStateException(uri + " → HTTP " + resp.statusCode());
                    continue;
                }
                Files.createDirectories(dest.getParent());
                Files.write(dest, resp.body());
                String hash = Digests.sha256Hex(dest);
                if (!hash.equalsIgnoreCase(f.getSha256())) {
                    last = new IllegalStateException("hash mismatch for " + f.getPath());
                    Files.deleteIfExists(dest);
                    continue;
                }
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalStateException("failed to fetch " + f.getPath(), last);
    }
}
