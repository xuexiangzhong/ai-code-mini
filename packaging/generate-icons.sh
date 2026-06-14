#!/usr/bin/env bash
#
# 从 packaging/icons/aicode-icon-1024.png 生成 macOS .icns 与 Windows .ico
# 依赖：macOS 自带 sips、iconutil；Node.js + npx（png-to-ico）
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ICON_DIR="${ROOT}/packaging/icons"
SOURCE="${ICON_DIR}/aicode-icon-1024.png"
RES_DIR="${ROOT}/src/main/resources/icons"

die() { echo "错误: $*" >&2; exit 1; }

[[ -f "$SOURCE" ]] || die "缺少源图: $SOURCE"

# 确保正方形
W=$(sips -g pixelWidth "$SOURCE" 2>/dev/null | awk '/pixelWidth/ {print $2}')
H=$(sips -g pixelHeight "$SOURCE" 2>/dev/null | awk '/pixelHeight/ {print $2}')
if [[ "$W" != "$H" ]]; then
    echo "==> 裁剪为 ${H}x${H} 正方形..."
    sips -c "$H" "$H" "$SOURCE" --out "$SOURCE" >/dev/null
fi

echo "==> 生成 aicode.icns ..."
ICONSET="${ICON_DIR}/aicode.iconset"
rm -rf "$ICONSET"
mkdir -p "$ICONSET"
for size in 16 32 128 256 512; do
    sips -z "$size" "$size" "$SOURCE" --out "${ICONSET}/icon_${size}x${size}.png" >/dev/null
    double=$((size * 2))
    sips -z "$double" "$double" "$SOURCE" --out "${ICONSET}/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$ICONSET" -o "${ICON_DIR}/aicode.icns"

echo "==> 生成 aicode.ico ..."
if command -v npx &>/dev/null; then
    npx --yes png-to-ico "$SOURCE" > "${ICON_DIR}/aicode.ico"
else
    die "需要 Node.js npx 以生成 .ico（或手动放置 aicode.ico）"
fi

echo "==> 复制 JavaFX 运行时图标 ..."
mkdir -p "$RES_DIR"
sips -z 256 256 "$SOURCE" --out "${RES_DIR}/aicode-256.png" >/dev/null
sips -z 32 32 "$SOURCE" --out "${RES_DIR}/aicode-32.png" >/dev/null

echo "完成:"
echo "  ${ICON_DIR}/aicode.icns"
echo "  ${ICON_DIR}/aicode.ico"
echo "  ${RES_DIR}/aicode-256.png"
