package com.cdrware.mycelium.agent;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Isolated so {@link LibtorrentFetcher} can catch ExceptionInInitializerError / UnsatisfiedLinkError.
 */
final class LibtorrentSession {
    private LibtorrentSession() {
    }

    static boolean download(Path torrentFile, Path dataDir, long timeoutMs) throws Exception {
        byte[] bytes = Files.readAllBytes(torrentFile);
        SessionManager sm = new SessionManager();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        sm.addListener(new AlertListener() {
            @Override
            public int[] types() {
                return null;
            }

            @Override
            public void alert(Alert<?> alert) {
                if (alert.type() == AlertType.TORRENT_FINISHED) {
                    finished.countDown();
                } else if (alert.type() == AlertType.TORRENT_ERROR) {
                    error.set(alert.message());
                    finished.countDown();
                }
            }
        });
        sm.start();
        try {
            TorrentInfo ti = TorrentInfo.bdecode(bytes);
            // Torrent info.name is "new" → files land in dataDir/new/...
            sm.download(ti, dataDir.toFile());
            if (!finished.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("jlibtorrent timed out");
            }
            if (error.get() != null) {
                throw new IllegalStateException(error.get());
            }
            return true;
        } finally {
            sm.stop();
        }
    }
}
