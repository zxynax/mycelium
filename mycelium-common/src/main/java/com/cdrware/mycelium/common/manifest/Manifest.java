package com.cdrware.mycelium.common.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Manifest {
    private String version;
    private List<String> groupIds = new ArrayList<>();
    private String infohash;
    private String torrentName;
    private List<ManifestFile> files = new ArrayList<>();
    private List<String> webseeds = new ArrayList<>();
    private List<String> announce = new ArrayList<>();
    private Instant createdAt;
    private String signature;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<String> groupIds) {
        this.groupIds = groupIds != null ? new ArrayList<>(groupIds) : new ArrayList<>();
    }

    public String getInfohash() {
        return infohash;
    }

    public void setInfohash(String infohash) {
        this.infohash = infohash;
    }

    public String getTorrentName() {
        return torrentName;
    }

    public void setTorrentName(String torrentName) {
        this.torrentName = torrentName;
    }

    public List<ManifestFile> getFiles() {
        return files;
    }

    public void setFiles(List<ManifestFile> files) {
        this.files = files != null ? new ArrayList<>(files) : new ArrayList<>();
    }

    public List<String> getWebseeds() {
        return webseeds;
    }

    public void setWebseeds(List<String> webseeds) {
        this.webseeds = webseeds != null ? new ArrayList<>(webseeds) : new ArrayList<>();
    }

    public List<String> getAnnounce() {
        return announce;
    }

    public void setAnnounce(List<String> announce) {
        this.announce = announce != null ? new ArrayList<>(announce) : new ArrayList<>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Manifest m)) return false;
        return Objects.equals(version, m.version)
                && Objects.equals(infohash, m.infohash)
                && Objects.equals(signature, m.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, infohash, signature);
    }
}
