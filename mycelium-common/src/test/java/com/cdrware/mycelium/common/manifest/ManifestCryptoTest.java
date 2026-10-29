package com.cdrware.mycelium.common.manifest;

import com.cdrware.mycelium.common.crypto.Ed25519;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestCryptoTest {
    @Test
    void signAndVerify() {
        KeyPair kp = Ed25519.generate();
        Manifest m = new Manifest();
        m.setVersion("1.0.0");
        m.setGroupIds(List.of("prod"));
        m.setInfohash("abc123");
        m.setFiles(List.of(new ManifestFile("a.jar", "deadbeef", 12)));
        m.setWebseeds(List.of("https://example/seed/"));
        m.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        ManifestCrypto.sign(m, kp.getPrivate());
        assertTrue(ManifestCrypto.verify(m, kp.getPublic()));
        m.setVersion("1.0.1");
        assertFalse(ManifestCrypto.verify(m, kp.getPublic()));
    }
}
