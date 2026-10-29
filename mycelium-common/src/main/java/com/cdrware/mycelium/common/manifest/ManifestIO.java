package com.cdrware.mycelium.common.manifest;

import com.cdrware.mycelium.common.crypto.Ed25519;
import com.cdrware.mycelium.common.json.Json;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ManifestIO {
    private ManifestIO() {
    }

    public static Manifest read(Path path) throws Exception {
        return Json.mapper().readValue(Files.readAllBytes(path), Manifest.class);
    }

    public static void write(Path path, Manifest manifest) throws Exception {
        Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
        Json.mapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), manifest);
    }

    public static Manifest readAndVerify(Path path, String publicKeyB64) throws Exception {
        Manifest m = read(path);
        if (!ManifestCrypto.verify(m, Ed25519.decodePublic(publicKeyB64))) {
            throw new SecurityException("manifest signature invalid: " + path);
        }
        return m;
    }
}
