#!/usr/bin/env bash
#
# macOS 安装包打包脚本
# 产出：dist/AiCode-<version>.dmg（默认）或 dist/AiCode.app（--app-image）
#
# 用法：
#   ./package-mac.sh              # 打 dmg 安装包
#   ./package-mac.sh --app-image  # 仅生成 .app 可执行程序（便于调试）
#   ./package-mac.sh --bundle     # 生成本地源码 zip（排除敏感配置，便于离线拷贝）
#
# 依赖：JDK 21+（含 jpackage）、Maven 3.8+
# 必须在 macOS 上运行。
#

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

# ── 应用元数据（与 pom.xml 保持一致）────────────────────────────────────────
APP_NAME="AiCode"
MAIN_CLASS="com.aicode.app.AiCodeMain"
DIST_DIR="dist"
INPUT_DIR="target/jpackage-input"
ICON_FILE="${ROOT_DIR}/packaging/icons/aicode.icns"
# jpackage 按 --add-modules 裁剪 JRE；OkHttp 需要 java.logging，WebView 需要 java.desktop 等
RUNTIME_MODULES="javafx.controls,javafx.fxml,javafx.web,java.logging,java.desktop,jdk.crypto.ec"
PACKAGE_TYPE="dmg"   # dmg | app-image
BUNDLE_ONLY=false
BUNDLE_ZIP="${DIST_DIR}/aiCodeMini-source.zip"

# ── 解析参数 ────────────────────────────────────────────────────────────────
for arg in "$@"; do
    case "$arg" in
        --app-image) PACKAGE_TYPE="app-image" ;;
        --bundle)    BUNDLE_ONLY=true ;;
        -h|--help)
            echo "用法: $0 [--app-image] [--bundle]"
            echo "  --app-image  生成 .app 可执行程序，不封装 dmg"
            echo "  --bundle     生成源码 zip（排除 API Key 等本地配置）"
            exit 0
            ;;
        *)
            echo "未知参数: $arg（可用 --app-image 或 --help）" >&2
            exit 1
            ;;
    esac
done

# ── 工具函数 ────────────────────────────────────────────────────────────────
info()  { echo "==> $*"; }
die()   { echo "错误: $*" >&2; exit 1; }

ensure_java() {
    if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home &>/dev/null; then
        JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
        export JAVA_HOME
    fi
    if [[ -n "${JAVA_HOME:-}" ]]; then
        export PATH="$JAVA_HOME/bin:$PATH"
    fi

    command -v java     &>/dev/null || die "未找到 java，请安装 JDK 21+ 并设置 JAVA_HOME"
    command -v jpackage &>/dev/null || die "未找到 jpackage，请使用完整 JDK（非 JRE）"

    local ver
    ver="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
    if [[ "${ver:-0}" -lt 21 ]]; then
        die "需要 Java 21+，当前版本: $(java -version 2>&1 | head -1)"
    fi
    info "Java: $(java -version 2>&1 | head -1)"
}

ensure_maven() {
    command -v mvn &>/dev/null || die "未找到 mvn，请安装 Maven 3.8+"
    info "Maven: $(mvn -version 2>&1 | head -1)"
}

ensure_macos() {
    [[ "$(uname -s)" == "Darwin" ]] || die "此脚本仅支持在 macOS 上运行"
}

read_version() {
    APP_VERSION="$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)"
    MAIN_JAR="$(mvn help:evaluate -Dexpression=project.build.finalName -q -DforceStdout).jar"
}

# jpackage 要求 app-version 首位整数 >= 1（0.1.0 会报错）
jpackage_app_version() {
    local major="${1%%.*}"
    local rest="${1#*.}"
    if [[ -z "${major}" || "${major}" == "0" ]]; then
        echo "1.${rest}"
    else
        echo "$1"
    fi
}

# 准备 jpackage 输入：应用 jar + JavaFX 平台依赖（含 .dylib）
prepare_jpackage_input() {
    rm -rf "${INPUT_DIR}"
    mkdir -p "${INPUT_DIR}/lib"
    cp "target/${MAIN_JAR}" "${INPUT_DIR}/"

    info "复制 JavaFX 依赖（含 macOS 原生库）..."
    mvn dependency:copy-dependencies \
        -DoutputDirectory="${INPUT_DIR}/lib" \
        -DincludeScope=runtime \
        -DincludeGroupIds=org.openjfx \
        -q

    local jfx_count
    jfx_count="$(find "${INPUT_DIR}/lib" -name 'javafx-*.jar' | wc -l | tr -d ' ')"
    [[ "${jfx_count}" -gt 0 ]] || die "未找到 JavaFX 依赖，请检查 pom.xml 中的 openjfx 配置"
}

bundle_source() {
    # shellcheck source=packaging/bundle-source.sh
    source "${ROOT_DIR}/packaging/bundle-source.sh"
    info "打包源码到 ${BUNDLE_ZIP} ..."
    bundle_source_zip "${BUNDLE_ZIP}"
    info "完成: ${BUNDLE_ZIP}"
}

# ── 主流程 ────────────────────────────────────────────────────────────────────
main() {
    ensure_macos
    ensure_java
    ensure_maven
    read_version

    info "应用: ${APP_NAME} ${APP_VERSION}"
    info "打包类型: ${PACKAGE_TYPE}"

    local jpkg_version
    jpkg_version="$(jpackage_app_version "${APP_VERSION}")"
    if [[ "${jpkg_version}" != "${APP_VERSION}" ]]; then
        info "jpackage 版本号: ${jpkg_version}（pom 为 ${APP_VERSION}，首位 0 需映射）"
    fi

    info "编译项目..."
    mvn clean package -DskipTests -q
    [[ -f "target/${MAIN_JAR}" ]] || die "未找到 target/${MAIN_JAR}"

    prepare_jpackage_input

    mkdir -p "${DIST_DIR}"
    info "执行 jpackage (--type ${PACKAGE_TYPE})..."

    # 非模块化项目：主 jar 走 classpath，JavaFX 走 module-path（jpackage 会内嵌 JRE）
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
    if [[ -f "${ICON_FILE}" ]]; then
        jpkg_args+=(--icon "${ICON_FILE}")
    else
        info "未找到 ${ICON_FILE}，跳过应用图标（可运行 packaging/generate-icons.sh）"
    fi
    jpackage "${jpkg_args[@]}"

    echo ""
    if [[ "${PACKAGE_TYPE}" == "dmg" ]]; then
        info "完成！安装包: ${DIST_DIR}/${APP_NAME}-${jpkg_version}.dmg"
        info "（若文件名略有差异，请查看 ${DIST_DIR}/ 目录）"
    else
        info "完成！可执行程序: ${DIST_DIR}/${APP_NAME}.app"
        info "直接运行: open ${DIST_DIR}/${APP_NAME}.app"
    fi
    info "配置请在启动前于工作目录准备 aicode.yaml"
}

if [[ "${BUNDLE_ONLY}" == true ]]; then
    bundle_source
    exit 0
fi

main "$@"
