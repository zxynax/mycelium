package com.cdrware.mycelium.common.torrent;

import com.cdrware.mycelium.common.hash.Digests;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a private multi-file BitTorrent v1 .torrent from a directory tree.
 */
public final class TorrentBuilder {
    public static final int DEFAULT_PIECE_LENGTH = 256 * 1024;

    private TorrentBuilder() {
    }

    public record Result(byte[] torrentBytes, String infohashHex, String name) {
    }

    public static Result build(
            Path root,
            List<Path> relativeFiles,
            String name,
            List<String> announceUrls,
            List<String> webseeds,
            int pieceLength
    ) throws IOException {
        if (relativeFiles.isEmpty()) {
            throw new IllegalArgumentException("no files");
        }
        List<Map<String, Object>> files = new ArrayList<>();
        List<Path> ordered = new ArrayList<>(relativeFiles);
        ordered.sort(Path::compareTo);

        ByteArrayOutput pieces = new ByteArrayOutput();
        byte[] pieceBuf = new byte[pieceLength];
        int piecePos = 0;

        for (Path rel : ordered) {
            Path abs = root.resolve(rel).normalize();
            if (!abs.startsWith(root.normalize())) {
                throw new IOException("path escapes root: " + rel);
            }
            long size = Files.size(abs);
            List<Object> pathParts = new ArrayList<>();
            for (Path part : rel) {
                pathParts.add(part.toString());
            }
            Map<String, Object> fileDict = new LinkedHashMap<>();
            fileDict.put("length", size);
            fileDict.put("path", pathParts);
            files.add(fileDict);

            try (InputStream in = Files.newInputStream(abs)) {
                int n;
                while ((n = in.read(pieceBuf, piecePos, pieceBuf.length - piecePos)) >= 0) {
                    if (n == 0) {
                        continue;
                    }
                    piecePos += n;
                    if (piecePos == pieceBuf.length) {
                        pieces.write(Digests.sha1(pieceBuf));
                        piecePos = 0;
                    }
                }
            }
        }
        if (piecePos > 0) {
            byte[] last = new byte[piecePos];
            System.arraycopy(pieceBuf, 0, last, 0, piecePos);
            pieces.write(Digests.sha1(last));
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", name);
        info.put("piece length", pieceLength);
        info.put("pieces", pieces.toByteArray());
        info.put("files", files);
        info.put("private", 1);

        byte[] infoBytes = Bencode.encode(info);
        String infohash = HexFormat.of().formatHex(Digests.sha1(infoBytes));

        Map<String, Object> rootDict = new LinkedHashMap<>();
        if (announceUrls != null && !announceUrls.isEmpty()) {
            rootDict.put("announce", announceUrls.get(0));
            if (announceUrls.size() > 1) {
                List<Object> tiers = new ArrayList<>();
                for (String a : announceUrls) {
                    tiers.add(List.of(a));
                }
                rootDict.put("announce-list", tiers);
            }
        }
        if (webseeds != null && !webseeds.isEmpty()) {
            // BEP-19 URL list
            rootDict.put("url-list", new ArrayList<>(webseeds));
        }
        rootDict.put("info", info);
        rootDict.put("creation date", System.currentTimeMillis() / 1000L);

        return new Result(Bencode.encode(rootDict), infohash, name);
    }

    /** Growable byte accumulator. */
    private static final class ByteArrayOutput {
        private byte[] buf = new byte[8192];
        private int size;

        void write(byte[] b) {
            ensure(size + b.length);
            System.arraycopy(b, 0, buf, size, b.length);
            size += b.length;
        }

        byte[] toByteArray() {
            byte[] out = new byte[size];
            System.arraycopy(buf, 0, out, 0, size);
            return out;
        }

        private void ensure(int need) {
            if (need <= buf.length) {
                return;
            }
            int n = buf.length;
            while (n < need) {
                n *= 2;
            }
            byte[] next = new byte[n];
            System.arraycopy(buf, 0, next, 0, size);
            buf = next;
        }
    }
}
