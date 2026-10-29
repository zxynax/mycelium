# Mycelium architecture

## Roles

- **Control plane (supernode):** groups, release manifests, scheduled rollouts, agent status, optional HTTPS webseed hosting.
- **Builder:** offline/CI tool that hashes a file tree, writes a private `.torrent`, and signs a manifest with Ed25519.
- **Agent (leaf):** polls the group’s active manifest, stages `new/` via hardlinks from `current/`, fills gaps with jlibtorrent and/or webseed HTTP, then atomically flips `current` ← `new` (previous → `old`).

## On-disk (agent)

```text
{datadir}/
  current/    # live tree
  new/        # staging (torrent info.name is "new")
  old/        # previous release (rollback)
  state.json  # last applied version / status
```

## Manifest

JSON fields: `version`, `groupIds`, `infohash`, `torrentName`, `files[{path,sha256,size}]`, `webseeds[]`, `announce[]`, `createdAt`, `signature` (Ed25519 over canonical JSON without `signature`).

Auth to the control API: shared secret via `X-Mycelium-Token` (raw secret or HMAC-SHA256 of `METHOD path`).

## Distribution

1. Always publish **web seeds** so a single HTTPS origin can cold-start nodes.
2. Optional private tracker announce URLs in the torrent/manifest.
3. Agent tries **jlibtorrent** when a `.torrent` is provided and natives load; otherwise webseed fetch verifies SHA-256 per file.

## Windows hardlink notes

Hardlinks require the same NTFS volume and fail when the source is locked (running EXE/DLL).

Mycelium already **falls back to copy** when `Files.createLink` fails (`DeployFs.hardlinkOrCopy`). Operators should:

- Keep `current` / `new` / `old` on the same volume.
- Stop services that lock binaries before flip when copy fallback is unacceptable for IO reasons.
- Prefer stop → flip → start for in-use Windows services.

Optional later: content-addressed `objects/sha256/…` store (OSTree-like).
