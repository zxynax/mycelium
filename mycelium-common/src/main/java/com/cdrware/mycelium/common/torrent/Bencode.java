package com.cdrware.mycelium.common.torrent;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Minimal bencode writer for torrent creation. */
public final class Bencode {
    private Bencode() {
    }

    public static byte[] encode(Object value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, value);
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static void write(ByteArrayOutputStream out, Object value) {
        if (value instanceof byte[] bytes) {
            writeBytes(out, bytes);
        } else if (value instanceof String s) {
            writeBytes(out, s.getBytes(StandardCharsets.UTF_8));
        } else if (value instanceof Number n) {
            out.write('i');
            writeRaw(out, Long.toString(n.longValue()));
            out.write('e');
        } else if (value instanceof List<?> list) {
            out.write('l');
            for (Object item : list) {
                write(out, item);
            }
            out.write('e');
        } else if (value instanceof Map<?, ?> map) {
            out.write('d');
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(e.getKey().toString(), e.getValue());
            }
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                writeBytes(out, e.getKey().getBytes(StandardCharsets.UTF_8));
                write(out, e.getValue());
            }
            out.write('e');
        } else {
            throw new IllegalArgumentException("unsupported bencode type: " + value.getClass());
        }
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        writeRaw(out, Integer.toString(bytes.length));
        out.write(':');
        out.writeBytes(bytes);
    }

    private static void writeRaw(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }
}
