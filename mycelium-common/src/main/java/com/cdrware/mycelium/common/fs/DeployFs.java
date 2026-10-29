package com.cdrware.mycelium.common.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Filesystem helpers: walk, hardlink-with-copy-fallback, atomic tree flip. */
public final class DeployFs {
    private DeployFs() {
    }

    public static List<Path> listRelativeFiles(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> out.add(root.relativize(p)));
        }
        out.sort(Path::compareTo);
        return out;
    }

    /**
     * Create a hardlink from {@code source} to {@code target}, falling back to copy
     * when hardlinks are unsupported or the source is locked (Windows mitigation).
     *
     * @return true if hardlinked, false if copied
     */
    public static boolean hardlinkOrCopy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.createLink(target, source);
            return true;
        } catch (UnsupportedOperationException | IOException e) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return false;
        }
    }

    /**
     * Atomic-ish flip: current→old, new→current. Best-effort delete of previous old.
     */
    public static void flip(Path dataDir) throws IOException {
        Path current = dataDir.resolve("current");
        Path neu = dataDir.resolve("new");
        Path old = dataDir.resolve("old");
        if (!Files.isDirectory(neu)) {
            throw new IOException("missing new/ staging tree");
        }
        if (Files.exists(old)) {
            deleteRecursive(old);
        }
        if (Files.exists(current)) {
            Files.move(current, old);
        }
        Files.move(neu, current);
    }

    public static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> all = walk.sorted((a, b) -> b.compareTo(a)).toList();
            for (Path p : all) {
                Files.deleteIfExists(p);
            }
        }
    }
}
