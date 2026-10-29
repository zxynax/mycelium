package com.cdrware.mycelium.agent;

import com.cdrware.mycelium.common.json.Json;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AgentState {
    private String nodeId;
    private String groupId;
    private String version;
    private String infohash;
    private String status;
    private String detail;
    private Instant updatedAt;

    public static AgentState load(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            return new AgentState();
        }
        return Json.mapper().readValue(Files.readAllBytes(path), AgentState.class);
    }

    public void save(Path path) throws Exception {
        updatedAt = Instant.now();
        Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
        Json.mapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), this);
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getInfohash() {
        return infohash;
    }

    public void setInfohash(String infohash) {
        this.infohash = infohash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
