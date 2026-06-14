#!/usr/bin/env bash
# Downloads Monaco Editor min assets into src/main/resources/editor/vs
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${MONACO_VERSION:-0.52.2}"
DEST="$ROOT/src/main/resources/editor/vs"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading monaco-editor@${VERSION}..."
curl -fsSL "https://registry.npmjs.org/monaco-editor/-/monaco-editor-${VERSION}.tgz" \
  | tar xz -C "$TMP"
rm -rf "$DEST"
cp -R "$TMP/package/min/vs" "$DEST"
echo "Installed to $DEST ($(du -sh "$DEST" | cut -f1))"
