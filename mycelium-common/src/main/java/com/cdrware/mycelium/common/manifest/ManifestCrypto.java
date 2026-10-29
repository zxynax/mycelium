package com.cdrware.mycelium.common.manifest;

import com.cdrware.mycelium.common.crypto.Ed25519;
import com.cdrware.mycelium.common.json.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/** Sign and verify manifests (ed25519 over canonical JSON without signature). */
public final class ManifestCrypto {
    private ManifestCrypto() {
    }

    public static void sign(Manifest manifest, PrivateKey privateKey) {
        try {
            byte[] payload = canonicalBytes(manifest);
            manifest.setSignature(Ed25519.sign(privateKey, payload));
        } catch (Exception e) {
            throw new IllegalStateException("manifest sign failed", e);
        }
    }

    public static boolean verify(Manifest manifest, PublicKey publicKey) {
        if (manifest.getSignature() == null || manifest.getSignature().isBlank()) {
            return false;
        }
        try {
            byte[] payload = canonicalBytes(manifest);
            return Ed25519.verify(publicKey, payload, manifest.getSignature());
        } catch (Exception e) {
            return false;
        }
    }

    static byte[] canonicalBytes(Manifest manifest) throws Exception {
        ObjectNode node = Json.canonical().valueToTree(manifest);
        node.remove("signature");
        ObjectNode sorted = sortObject(node);
        return Json.canonical().writeValueAsBytes(sorted);
    }

    private static ObjectNode sortObject(ObjectNode in) {
        TreeMap<String, JsonNode> map = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = in.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v != null && v.isObject()) {
                map.put(e.getKey(), sortObject((ObjectNode) v));
            } else {
                map.put(e.getKey(), v);
            }
        }
        ObjectNode out = Json.canonical().createObjectNode();
        map.forEach(out::set);
        return out;
    }
}
