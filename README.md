# Mycelium

Torrent-backed node deploy: **control plane**, **node agent**, and **release builder**.

| Module | Role |
|--------|------|
| `mycelium-common` | Signed manifest, Ed25519, HMAC token, SHA-256, bencode torrent builder, hardlink helpers |
| `mycelium-builder` | CLI: artifact tree → `.torrent` + signed manifest |
| `mycelium-agent` | Poll / stage (hardlink) / fetch (jlibtorrent or HTTPS webseed) / atomic flip / status |
| `mycelium-control` | REST API + simple UI: groups, releases, rollouts, node status |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn -q test
mvn -q -pl mycelium-builder,mycelium-agent,mycelium-control -am package
```

jlibtorrent comes from FrostWire’s Maven repo (`dl.frostwire.com`); Linux x86_64 natives are pulled for the agent. If natives fail to load, the agent **falls back to HTTPS web seeds**.

## Quick start

```bash
# 1) Signing keys
java -jar mycelium-builder/target/mycelium-builder-0.1.0-SNAPSHOT.jar gen-keys --out ./keys

# 2) Build a release from a directory of files
mkdir -p dist && echo hi > dist/app.txt
java -jar mycelium-builder/target/mycelium-builder-0.1.0-SNAPSHOT.jar \
  --input ./dist --out ./release --version 1.0.0 --group default \
  --webseed http://127.0.0.1:8080/seed/1.0.0/ \
  --signing-key ./keys/ed25519.priv

# Serve files at /seed/1.0.0/... — copy tree next to control webroot:
mkdir -p webroot/1.0.0 && cp -a dist/. webroot/1.0.0/

# 3) Control plane
java -jar mycelium-control/target/mycelium-control-0.1.0-SNAPSHOT.jar \
  --port 8080 --api-token secret \
  --seed-manifest ./release/1.0.0.manifest.json \
  --webroot ./webroot

# 4) Agent (once)
java -jar mycelium-agent/target/mycelium-agent-0.1.0-SNAPSHOT.jar \
  --public-key ./keys/ed25519.pub \
  --data-dir ./node-data --node-id node-1 --group-id default \
  --control-url http://127.0.0.1:8080 --api-token secret \
  --torrent ./release/1.0.0.torrent --once
```

Open `http://127.0.0.1:8080/` for the UI.
