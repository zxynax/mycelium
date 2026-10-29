package com.cdrware.mycelium.common.crypto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Ed25519 sign/verify helpers (JDK 15+). */
public final class Ed25519 {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private Ed25519() {
    }

    public static KeyPair generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Ed25519 unavailable", e);
        }
    }

    public static String encodePublic(PublicKey key) {
        return B64.encodeToString(key.getEncoded());
    }

    public static String encodePrivate(PrivateKey key) {
        return B64.encodeToString(key.getEncoded());
    }

    public static PublicKey decodePublic(String b64) {
        try {
            byte[] raw = B64D.decode(b64);
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(raw));
        } catch (Exception e) {
            throw new IllegalArgumentException("bad public key", e);
        }
    }

    public static PrivateKey decodePrivate(String b64) {
        try {
            byte[] raw = B64D.decode(b64);
            return KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(raw));
        } catch (Exception e) {
            throw new IllegalArgumentException("bad private key", e);
        }
    }

    public static String sign(PrivateKey privateKey, byte[] message) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(privateKey);
            sig.update(message);
            return B64.encodeToString(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException("sign failed", e);
        }
    }

    public static boolean verify(PublicKey publicKey, byte[] message, String signatureB64) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(message);
            return sig.verify(B64D.decode(signatureB64));
        } catch (Exception e) {
            return false;
        }
    }
}
