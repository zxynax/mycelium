package com.cdrware.mycelium.agent;

import com.cdrware.mycelium.common.fs.DeployFs;
import com.cdrware.mycelium.common.hash.Digests;
import com.cdrware.mycelium.common.manifest.Manifest;
import com.cdrware.mycelium.common.manifest.ManifestFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds {@code new/} by hardlinking unchanged files from {@code current/} (SHA-256 match),
 * leaving missing/changed paths empty for the fetcher to fill.
 */
public final class HardlinkStager {
    public record Stats(int hardlinked, int copied, int missing) {
    }

    private HardlinkStager() {
    }

    public static Stats stage(Path dataDir, Manifest manifest) throws Exception {
        Path current = dataDir.resolve("current");
        Path neu = dataDir.resolve("new");
        if (Files.exists(neu)) {
            DeployFs.deleteRecursive(neu);
        }
        Files.createDirectories(neu);

        Map<String, String> currentHashes = indexCurrent(current);
        int linked = 0;
        int copied = 0;
        int missing = 0;

        for (ManifestFile f : manifest.getFiles()) {
            Path dest = neu.resolve(f.getPath()).normalize();
            if (!dest.startsWith(neu)) {
                throw new SecurityException("bad path: " + f.getPath());
            }
            String curHash = currentHashes.get(f.getPath());
            if (curHash != null && curHash.equalsIgnoreCase(f.getSha256())) {
                Path src = current.resolve(f.getPath());
                if (DeployFs.hardlinkOrCopy(src, dest)) {
                    linked++;
                } else {
                    copied++;
                }
            } else {
                missing++;
                Files.createDirectories(dest.getParent());
            }
        }
        return new Stats(linked, copied, missing);
    }

    private static Map<String, String> indexCurrent(Path current) throws Exception {
        Map<String, String> map = new HashMap<>();
        if (!Files.isDirectory(current)) {
            return map;
        }
        for (Path rel : DeployFs.listRelativeFiles(current)) {
            String key = rel.toString().replace('\\', '/');
            map.put(key, Digests.sha256Hex(current.resolve(rel)));
        }
        return map;
    }
}
