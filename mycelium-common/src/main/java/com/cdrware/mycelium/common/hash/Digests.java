package com.cdrware.mycelium.common.hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Digests {
    private Digests() {
    }

    public static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("SHA-256 failed", e);
        }
    }

    public static String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
