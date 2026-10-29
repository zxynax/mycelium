package com.cdrware.mycelium.common.manifest;

import java.util.Objects;

public final class ManifestFile {
    private String path;
    private String sha256;
    private long size;

    public ManifestFile() {
    }

    public ManifestFile(String path, String sha256, long size) {
        this.path = path;
        this.sha256 = sha256;
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ManifestFile f)) return false;
        return size == f.size && Objects.equals(path, f.path) && Objects.equals(sha256, f.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, sha256, size);
    }
}
