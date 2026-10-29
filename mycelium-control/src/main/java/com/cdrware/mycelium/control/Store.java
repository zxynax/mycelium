package com.cdrware.mycelium.control;

import com.cdrware.mycelium.common.manifest.Manifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory control-plane store (v1). */
public final class Store {
    public final Map<String, NodeGroup> groups = new ConcurrentHashMap<>();
    public final Map<String, Manifest> releases = new ConcurrentHashMap<>();
    public final Map<String, Rollout> rollouts = new ConcurrentHashMap<>();
    public final Map<String, NodeStatus> nodes = new ConcurrentHashMap<>();

    public static final class NodeGroup {
        public String id;
        public String name;
        public String activeVersion;
        public Instant rolloutAt;

        public NodeGroup() {
        }

        public NodeGroup(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Rollout {
        public String id;
        public String groupId;
        public String version;
        public Instant scheduledAt;
        public String status = "pending";
    }

    public static final class NodeStatus {
        public String nodeId;
        public String groupId;
        public String version;
        public String infohash;
        public String status;
        public String detail;
        public Instant updatedAt;
    }

    public List<NodeGroup> listGroups() {
        return new ArrayList<>(groups.values());
    }

    public List<Manifest> listReleases() {
        return new ArrayList<>(releases.values());
    }

    public List<NodeStatus> listNodes() {
        return new ArrayList<>(nodes.values());
    }
}
