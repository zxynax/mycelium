package com.cdrware.mycelium.control;

import com.cdrware.mycelium.common.crypto.HmacToken;
import com.cdrware.mycelium.common.json.Json;
import com.cdrware.mycelium.common.manifest.Manifest;
import com.cdrware.mycelium.common.manifest.ManifestIO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Control plane: groups, releases, rollouts, agent status, simple HTML UI.
 */
public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Args a = Args.parse(args);
        Store store = new Store();
        store.groups.put("default", new Store.NodeGroup("default", "Default"));

        if (a.seedManifest != null) {
            Manifest m = ManifestIO.read(Path.of(a.seedManifest));
            store.releases.put(m.getVersion(), m);
            store.groups.get("default").activeVersion = m.getVersion();
            log.info("Seeded release {}", m.getVersion());
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(a.bind, a.port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/", ex -> serveUi(ex));
        server.createContext("/api/groups", ex -> {
            if (!auth(ex, a.apiToken)) {
                return;
            }
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                json(ex, 200, store.listGroups());
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                Store.NodeGroup g = Json.mapper().readValue(ex.getRequestBody(), Store.NodeGroup.class);
                if (g.id == null || g.id.isBlank()) {
                    g.id = UUID.randomUUID().toString();
                }
                store.groups.put(g.id, g);
                json(ex, 200, g);
            } else {
                text(ex, 405, "method");
            }
        });
        server.createContext("/api/releases", ex -> {
            if (!auth(ex, a.apiToken)) {
                return;
            }
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                json(ex, 200, store.listReleases());
            } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                Manifest m = Json.mapper().readValue(ex.getRequestBody(), Manifest.class);
                store.releases.put(m.getVersion(), m);
                json(ex, 200, Map.of("version", m.getVersion()));
            } else {
                text(ex, 405, "method");
            }
        });
        server.createContext("/api/rollouts", ex -> {
            if (!auth(ex, a.apiToken)) {
                return;
            }
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                Store.Rollout r = Json.mapper().readValue(ex.getRequestBody(), Store.Rollout.class);
                r.id = UUID.randomUUID().toString();
                if (r.scheduledAt == null) {
                    r.scheduledAt = Instant.now();
                }
                Store.NodeGroup g = store.groups.get(r.groupId);
                if (g == null) {
                    text(ex, 404, "group not found");
                    return;
                }
                if (!store.releases.containsKey(r.version)) {
                    text(ex, 404, "release not found");
                    return;
                }
                if (!r.scheduledAt.isAfter(Instant.now())) {
                    g.activeVersion = r.version;
                    g.rolloutAt = Instant.now();
                    r.status = "active";
                } else {
                    r.status = "scheduled";
                }
                store.rollouts.put(r.id, r);
                json(ex, 200, r);
            } else if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                json(ex, 200, store.rollouts.values());
            } else {
                text(ex, 405, "method");
            }
        });
        server.createContext("/api/nodes", ex -> {
            String path = ex.getRequestURI().getPath();
            if (path.matches("/api/nodes/[^/]+/status") && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                if (!auth(ex, a.apiToken)) {
                    return;
                }
                String nodeId = path.substring("/api/nodes/".length(), path.length() - "/status".length());
                Store.NodeStatus st = Json.mapper().readValue(ex.getRequestBody(), Store.NodeStatus.class);
                st.nodeId = nodeId;
                st.updatedAt = Instant.now();
                store.nodes.put(nodeId, st);
                json(ex, 200, Map.of("ok", true));
                return;
            }
            if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && "/api/nodes".equals(path)) {
                if (!auth(ex, a.apiToken)) {
                    return;
                }
                json(ex, 200, store.listNodes());
                return;
            }
            text(ex, 404, "not found");
        });

        // Group manifest for agents (apply scheduled rollouts that are due)
        server.createContext("/api/groups/", ex -> {
            String path = ex.getRequestURI().getPath();
            if (!path.endsWith("/manifest")) {
                text(ex, 404, "not found");
                return;
            }
            if (!auth(ex, a.apiToken)) {
                return;
            }
            String groupId = path.substring("/api/groups/".length(), path.length() - "/manifest".length());
            applyDueRollouts(store);
            Store.NodeGroup g = store.groups.get(groupId);
            if (g == null || g.activeVersion == null) {
                text(ex, 404, "no active release");
                return;
            }
            Manifest m = store.releases.get(g.activeVersion);
            if (m == null) {
                text(ex, 404, "release missing");
                return;
            }
            json(ex, 200, m);
        });

        // Public webseed of release files if --webroot set
        if (a.webroot != null) {
            Path root = Path.of(a.webroot).toAbsolutePath().normalize();
            server.createContext("/seed/", ex -> {
                String rel = ex.getRequestURI().getPath().substring("/seed/".length());
                Path file = root.resolve(rel).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                    text(ex, 404, "missing");
                    return;
                }
                byte[] bytes = Files.readAllBytes(file);
                ex.getResponseHeaders().add("Content-Type", "application/octet-stream");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(bytes);
                }
            });
        }

        server.start();
        log.info("Mycelium control listening on http://{}:{}/", a.bind, a.port);
    }

    private static void applyDueRollouts(Store store) {
        Instant now = Instant.now();
        for (Store.Rollout r : store.rollouts.values()) {
            if ("scheduled".equals(r.status) && r.scheduledAt != null && !r.scheduledAt.isAfter(now)) {
                Store.NodeGroup g = store.groups.get(r.groupId);
                if (g != null) {
                    g.activeVersion = r.version;
                    g.rolloutAt = now;
                    r.status = "active";
                }
            }
        }
    }

    private static boolean auth(HttpExchange ex, String secret) throws IOException {
        if (secret == null || secret.isBlank()) {
            return true;
        }
        String provided = ex.getRequestHeaders().getFirst("X-Mycelium-Token");
        String payload = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
        // Also accept raw shared secret for simple curl/UI
        if (secret.equals(provided) || HmacToken.matches(secret, payload, provided == null ? "" : provided)) {
            return true;
        }
        text(ex, 401, "unauthorized");
        return false;
    }

    private static void serveUi(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            text(ex, 405, "method");
            return;
        }
        String html = UI_HTML;
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void json(HttpExchange ex, int code, Object body) throws IOException {
        byte[] bytes = Json.mapper().writeValueAsBytes(body);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void text(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static final String UI_HTML = """
            <!doctype html>
            <html><head><meta charset="utf-8"><title>Mycelium Control</title>
            <style>
              body{font-family:system-ui,sans-serif;margin:2rem;background:#0f1419;color:#e7ecf1}
              h1{font-weight:600} a{color:#6cb6ff}
              .card{background:#1a2332;padding:1rem 1.25rem;border-radius:8px;margin:1rem 0}
              input,button{padding:.4rem .6rem;margin:.2rem;border-radius:4px;border:1px solid #334}
              button{background:#2b6cb0;color:#fff;border:none;cursor:pointer}
              pre{background:#0b1016;padding:1rem;overflow:auto}
            </style></head><body>
            <h1>Mycelium</h1>
            <p>Torrent-backed deploy control plane (v1).</p>
            <div class="card">
              <label>API token <input id="tok" type="password" placeholder="optional"></label>
              <button onclick="loadAll()">Refresh</button>
            </div>
            <div class="card"><h3>Groups</h3><pre id="groups">—</pre></div>
            <div class="card"><h3>Releases</h3><pre id="releases">—</pre></div>
            <div class="card"><h3>Nodes</h3><pre id="nodes">—</pre></div>
            <div class="card">
              <h3>Rollout</h3>
              <input id="gid" placeholder="group id" value="default">
              <input id="ver" placeholder="version">
              <button onclick="roll()">Activate now</button>
            </div>
            <script>
            const tok=()=>document.getElementById('tok').value;
            async function api(path, opts={}){
              const h=Object.assign({'Content-Type':'application/json'}, opts.headers||{});
              if(tok()) h['X-Mycelium-Token']=tok();
              const r=await fetch(path,{...opts,headers:h});
              if(!r.ok) throw new Error(await r.text());
              return r.json();
            }
            async function loadAll(){
              try{
                document.getElementById('groups').textContent=JSON.stringify(await api('/api/groups'),null,2);
                document.getElementById('releases').textContent=JSON.stringify(await api('/api/releases'),null,2);
                document.getElementById('nodes').textContent=JSON.stringify(await api('/api/nodes'),null,2);
              }catch(e){alert(e)}
            }
            async function roll(){
              try{
                await api('/api/rollouts',{method:'POST',body:JSON.stringify({
                  groupId:document.getElementById('gid').value,
                  version:document.getElementById('ver').value
                })});
                loadAll();
              }catch(e){alert(e)}
            }
            loadAll();
            </script></body></html>
            """;

    static final class Args {
        String bind = "0.0.0.0";
        int port = 8080;
        String apiToken;
        String seedManifest;
        String webroot;

        static Args parse(String[] argv) {
            Args a = new Args();
            for (int i = 0; i < argv.length; i++) {
                switch (argv[i]) {
                    case "--bind" -> a.bind = argv[++i];
                    case "--port" -> a.port = Integer.parseInt(argv[++i]);
                    case "--api-token" -> a.apiToken = argv[++i];
                    case "--seed-manifest" -> a.seedManifest = argv[++i];
                    case "--webroot" -> a.webroot = argv[++i];
                    case "--help", "-h" -> {
                        System.out.println("""
                                mycelium-control [--bind HOST] [--port N] [--api-token SECRET] \\
                                  [--seed-manifest PATH] [--webroot DIR]
                                """);
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("unknown: " + argv[i]);
                }
            }
            return a;
        }
    }
}
