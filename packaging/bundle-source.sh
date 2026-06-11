# Shared source bundle helper for package-mac.sh / package-win.sh.
# Excludes build artifacts, IDE junk, and local config that may contain API keys.

bundle_source_zip() {
    local output_zip="$1"
    mkdir -p "$(dirname "$output_zip")"
    rm -f "$output_zip"
    zip -rq "$output_zip" . \
        -x "target/*" \
        -x "dist/*" \
        -x ".git/*" \
        -x ".tools/*" \
        -x "*.iml" \
        -x ".idea/*" \
        -x ".DS_Store" \
        -x "*/.DS_Store" \
        -x "packaging/icons/*.iconset/*" \
        -x "aicode.yaml" \
        -x "aicodeing.yaml" \
        -x ".aicode/*" \
        -x ".env" \
        -x ".env.*" \
        -x "local.properties"
}
