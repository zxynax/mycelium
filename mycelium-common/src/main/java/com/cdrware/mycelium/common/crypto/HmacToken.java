package com.cdrware.mycelium.common.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Shared HMAC token auth between agents and control plane. */
public final class HmacToken {
    private HmacToken() {
    }

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    public static boolean matches(String secret, String payload, String providedHex) {
        if (secret == null || providedHex == null) {
            return false;
        }
        String expected = sign(secret, payload);
        return constantTimeEquals(expected, providedHex.toLowerCase());
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
