package com.cdrware.mycelium.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Optional jlibtorrent integration. Compilation uses frostwire APIs when present;
 * any load/runtime failure returns {@code false} from {@link #tryDownload} so the
 * agent can fall back to HTTPS web seeds.
 */
public final class LibtorrentFetcher {
    private static final Logger log = LoggerFactory.getLogger(LibtorrentFetcher.class);

    private LibtorrentFetcher() {
    }

    public static boolean tryDownload(Path torrentFile, Path dataDir, long timeoutMs) {
        try {
            return LibtorrentSession.download(torrentFile, dataDir, timeoutMs);
        } catch (Throwable t) {
            log.warn("jlibtorrent unavailable or failed: {}", t.toString());
            return false;
        }
    }
}
