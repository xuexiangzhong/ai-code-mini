#!/usr/bin/env bash
#
# Windows 安装包打包脚本（纯本地，不上传代码）
#
# 运行环境：Windows（Git Bash / MSYS2 / MINGW64）
#
# 用法：
#   bash package-win.sh                # 打 exe 安装包（需 WiX）
#   bash package-win.sh --app-image    # 便携目录 dist/AiCode/（不需要 WiX，推荐先试）
#
# 在 macOS 上（无法交叉编译）：
#   ./package-win.sh --bundle          # 生成本地源码 zip，拷到 Windows 后执行上面命令
#
# 依赖：JDK 21+（完整 JDK）、Maven 3.8+；打 exe 时需 WiX 3.14+（脚本会尝试自动安装）
#

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

APP_NAME="AiCode"
MAIN_CLASS="com.aicode.app.AiCodeMain"
DIST_DIR="dist"
INPUT_DIR="target/jpackage-input"
ICON_FILE="${ROOT_DIR}/packaging/icons/aicode.ico"
RUNTIME_MODULES="javafx.controls,javafx.fxml,javafx.web,java.logging,java.desktop,jdk.crypto.ec"
PACKAGE_TYPE="exe"
BUNDLE_ONLY=false
WIX_VERSION="3.14.1"
WIX_URL="https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"

for arg in "$@"; do
    case "$arg" in
        --app-image) PACKAGE_TYPE="app-image" ;;
        --bundle)    BUNDLE_ONLY=true ;;
        -h|--help)
            cat <<'EOF'
用法: bash package-win.sh [选项]

选项:
  --app-image  便携版 dist/AiCode/AiCode.exe（不需要 WiX，推荐先验证）
  --bundle     在 macOS 上生成源码 zip，拷到 Windows 后再打包
  -h, --help   显示帮助

注意:
  - 必须在 Windows 本机运行（jpackage 不支持 Mac 交叉编译）。
  - JavaFX 依赖必须在 Windows 上 copy（含 win 原生库），不要用 Mac 上的 target/。
  - 也可使用 PowerShell: .\package-win.ps1 [-AppImage]
EOF
            exit 0
            ;;
        *)
            echo "未知参数: $arg（可用 --help）" >&2
            exit 1
            ;;
    esac
done

TOOLS_DIR="${ROOT_DIR}/.tools"
WIX_DIR="${TOOLS_DIR}/wix"
BUNDLE_ZIP="${DIST_DIR}/aiCodeMini-windows-build.zip"

info()  { echo "==> $*"; }
die()   { echo "错误: $*" >&2; exit 1; }

read_version() {
    APP_VERSION="$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)"
    MAIN_JAR="$(mvn help:evaluate -Dexpression=project.build.finalName -q -DforceStdout).jar"
}

jpackage_app_version() {
    local major="${1%%.*}"
    local rest="${1#*.}"
    if [[ -z "${major}" || "${major}" == "0" ]]; then
        echo "1.${rest}"
    else
        echo "$1"
    fi
}

ensure_not_wsl() {
    if grep -qi microsoft /proc/version 2>/dev/null; then
        die "检测到 WSL。jpackage 在 WSL 里只能打 Linux 包，请在 Windows 本机 Git Bash / PowerShell 中运行。"
    fi
}

ensure_java() {
    # 常见 Windows JDK 安装路径
    if [[ -z "${JAVA_HOME:-}" ]]; then
        for dir in \
            "/c/Program Files/Java/jdk-21" \
            "/c/Program Files/Eclipse Adoptium/jdk-21"* \
            "/c/Program Files/Microsoft/jdk-21"*; do
            if [[ -d "$dir" && -x "$dir/bin/java.exe" ]]; then
                JAVA_HOME="$dir"
                export JAVA_HOME
                break
            fi
        done
    fi
    if [[ -n "${JAVA_HOME:-}" ]]; then
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
    command -v java.exe &>/dev/null && java() { java.exe "$@"; }
    command -v jpackage.exe &>/dev/null && jpackage() { jpackage.exe "$@"; }
    command -v java     &>/dev/null || die "未找到 java，请安装 JDK 21+ 并设置 JAVA_HOME"
    command -v jpackage &>/dev/null || die "未找到 jpackage，请使用完整 JDK（非 JRE）"
    info "Java: $(java -version 2>&1 | head -1)"
}

ensure_maven() {
    command -v mvn &>/dev/null || die "未找到 mvn，请安装 Maven 3.8+"
    info "Maven: $(mvn -version 2>&1 | head -1)"
}

append_wix_to_path() {
    local dir
    for dir in \
        "${WIX_DIR}" \
        "/c/Program Files (x86)/WiX Toolset v3.14/bin" \
        "/c/Program Files/WiX Toolset v3.14/bin" \
        "/c/Program Files (x86)/WiX Toolset v3.11/bin"; do
        if [[ -f "${dir}/candle.exe" ]]; then
            export PATH="${dir}:${PATH}"
            return 0
        fi
    done
    return 1
}

wix_available() {
    append_wix_to_path
    command -v candle.exe &>/dev/null || command -v candle &>/dev/null
}

install_wix_via_winget() {
    command -v winget.exe &>/dev/null || return 1
    info "尝试 winget 安装 WiX Toolset..."
    winget install --id WiXToolset.WiXToolset -e \
        --accept-package-agreements --accept-source-agreements 2>/dev/null && return 0
    winget install --id FireGiant.WiXToolset -e \
        --accept-package-agreements --accept-source-agreements 2>/dev/null && return 0
    return 1
}

install_wix_via_choco() {
    command -v choco.exe &>/dev/null || return 1
    info "尝试 chocolatey 安装 wixtoolset..."
    choco install wixtoolset -y --no-progress
}

install_wix_via_download() {
    info "下载 WiX ${WIX_VERSION} 便携版到 ${WIX_DIR} ..."
    mkdir -p "${TOOLS_DIR}"
    local zip="${TOOLS_DIR}/wix314-binaries.zip"
    if command -v curl.exe &>/dev/null; then
        curl.exe -fsSL -o "${zip}" "${WIX_URL}"
    elif command -v curl &>/dev/null; then
        curl -fsSL -o "${zip}" "${WIX_URL}"
    else
        die "需要 curl 以下载 WiX"
    fi
    rm -rf "${WIX_DIR}"
    mkdir -p "${WIX_DIR}"
    if command -v unzip &>/dev/null; then
        unzip -qo "${zip}" -d "${WIX_DIR}"
    else
        powershell.exe -NoProfile -Command \
            "Expand-Archive -Path '${zip//\//\\}' -DestinationPath '${WIX_DIR//\//\\}' -Force"
    fi
}

ensure_wix() {
    wix_available && { info "WiX: 已就绪"; return 0; }
    info "未检测到 WiX Toolset（jpackage 打 exe 需要）..."
    install_wix_via_winget    && append_wix_to_path && wix_available && return 0
    install_wix_via_choco       && append_wix_to_path && wix_available && return 0
    install_wix_via_download    && append_wix_to_path && wix_available && return 0
    die "无法安装 WiX。请改用: bash package-win.sh --app-image"
}

prepare_jpackage_input() {
    rm -rf "${INPUT_DIR}"
    mkdir -p "${INPUT_DIR}/lib"
    cp "target/${MAIN_JAR}" "${INPUT_DIR}/"

    info "复制 JavaFX 依赖（须为 Windows 版 win classifier）..."
    mvn dependency:copy-dependencies \
        -DoutputDirectory="${INPUT_DIR}/lib" \
        -DincludeScope=runtime \
        -DincludeGroupIds=org.openjfx \
        -q

    if find "${INPUT_DIR}/lib" -name 'javafx-*-mac.jar' 2>/dev/null | grep -q .; then
        die "检测到 macOS 版 JavaFX jar。请删除 target/ 后在 Windows 本机重新运行本脚本。"
    fi
    local jfx_count
    jfx_count="$(find "${INPUT_DIR}/lib" -name 'javafx-*.jar' 2>/dev/null | wc -l | tr -d ' ')"
    [[ "${jfx_count}" -gt 0 ]] || die "未找到 JavaFX 依赖"
    info "JavaFX jar 数量: ${jfx_count}"
}

clean_dist_output() {
    local jpkg_version="$1"
    rm -rf "${DIST_DIR}/${APP_NAME}" 2>/dev/null || true
    rm -f "${DIST_DIR}/${APP_NAME}-${jpkg_version}.exe" 2>/dev/null || true
}

bundle_for_windows() {
    [[ "$(uname -s)" == "Darwin" ]] || die "--bundle 仅用于 macOS"
    # shellcheck source=packaging/bundle-source.sh
    source "${ROOT_DIR}/packaging/bundle-source.sh"
    info "打包源码到 ${BUNDLE_ZIP} ..."
    bundle_source_zip "${BUNDLE_ZIP}"
    info "完成: ${BUNDLE_ZIP}"
    cat <<'EOF'

下一步（Mac → Windows，代码不上传云端）：
  1. 将 zip 拷到 Windows（U 盘 / 局域网 / Parallels 共享文件夹）
  2. 解压，安装 JDK 21、Maven
  3. 推荐先试便携版:  bash package-win.sh --app-image
  4. 或打安装包:      bash package-win.sh
     （也可用 PowerShell: .\package-win.ps1）
EOF
}

build_on_windows() {
    ensure_not_wsl
    ensure_java
    ensure_maven
    read_version

    if [[ "${PACKAGE_TYPE}" == "exe" ]]; then
        ensure_wix
    fi

    local jpkg_version
    jpkg_version="$(jpackage_app_version "${APP_VERSION}")"

    info "应用: ${APP_NAME} ${APP_VERSION} → jpackage ${jpkg_version}"
    info "打包类型: ${PACKAGE_TYPE}"

    info "编译项目..."
    mvn clean package -DskipTests -q
    [[ -f "target/${MAIN_JAR}" ]] || die "未找到 target/${MAIN_JAR}"

    prepare_jpackage_input
    mkdir -p "${DIST_DIR}"
    clean_dist_output "${jpkg_version}"

    info "执行 jpackage..."
    local -a jpkg_args=(
        --type "${PACKAGE_TYPE}"
        --name "${APP_NAME}"
        --app-version "${jpkg_version}"
        --dest "${DIST_DIR}"
        --input "${INPUT_DIR}"
        --main-jar "${MAIN_JAR}"
        --main-class "${MAIN_CLASS}"
        --module-path "${INPUT_DIR}/lib"
        --add-modules "${RUNTIME_MODULES}"
        --java-options "-Xmx1g"
    )
    if [[ "${PACKAGE_TYPE}" == "exe" ]]; then
        jpkg_args+=(--win-dir-chooser --win-menu --win-shortcut)
    fi
    if [[ -f "${ICON_FILE}" ]]; then
        jpkg_args+=(--icon "${ICON_FILE}")
    else
        info "未找到 ${ICON_FILE}，跳过应用图标（可运行 packaging/generate-icons.sh）"
    fi
    jpackage "${jpkg_args[@]}"

    echo ""
    if [[ "${PACKAGE_TYPE}" == "exe" ]]; then
        info "完成！安装包: ${DIST_DIR}/${APP_NAME}-${jpkg_version}.exe"
    else
        info "完成！运行: ${DIST_DIR}/${APP_NAME}/${APP_NAME}.exe"
    fi
    info "配置默认在 %USERPROFILE%\\aicode.yaml，可在应用内切换工作区"
}

# ── 入口 ────────────────────────────────────────────────────────────────────
if [[ "${BUNDLE_ONLY}" == true ]]; then
    bundle_for_windows
    exit 0
fi

case "$(uname -s)" in
    Darwin)
        echo "jpackage 无法在 macOS 上打 Windows exe。" >&2
        echo "请运行: ./package-win.sh --bundle" >&2
        exit 1
        ;;
    MINGW*|MSYS*|CYGWIN*|Windows_NT)
        build_on_windows
        ;;
    Linux)
        die "Linux/WSL 无法打 Windows exe。请在 Windows 本机运行。"
        ;;
    *)
        die "不支持的操作系统: $(uname -s)"
        ;;
esac
