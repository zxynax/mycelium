package com.cdrware.mycelium.agent;

import com.cdrware.mycelium.common.hash.Digests;
import com.cdrware.mycelium.common.manifest.Manifest;
import com.cdrware.mycelium.common.manifest.ManifestFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardlinkStagerTest {
    @TempDir
    Path dir;

    @Test
    void hardlinksUnchangedFiles() throws Exception {
        Path data = dir.resolve("data");
        Path current = data.resolve("current");
        Files.createDirectories(current);
        Path f = current.resolve("app.txt");
        Files.writeString(f, "hello");
        String hash = Digests.sha256Hex(f);

        Manifest m = new Manifest();
        m.setVersion("1");
        m.setFiles(List.of(new ManifestFile("app.txt", hash, Files.size(f))));

        HardlinkStager.Stats stats = HardlinkStager.stage(data, m);
        assertEquals(1, stats.hardlinked() + stats.copied());
        assertEquals(0, stats.missing());
        assertTrue(Files.exists(data.resolve("new/app.txt")));
        assertEquals("hello", Files.readString(data.resolve("new/app.txt")));
    }
}
