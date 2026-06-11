# AiCode 安装包打包指南

将本项目打成 **macOS `.dmg`** 或 **Windows `.exe`** 安装包。项目使用 Java 21 + JavaFX，通过 Maven 编译后，用 JDK 自带的 `jpackage` 生成原生安装包（内嵌 JRE，用户无需单独安装 Java）。

## 应用图标

安装包与窗口图标位于 `packaging/icons/`：

| 文件 | 用途 |
|------|------|
| `aicode-icon-1024.png` | 源图（1024×1024） |
| `aicode.icns` | macOS jpackage `--icon` |
| `aicode.ico` | Windows jpackage `--icon` |

修改图标后重新生成：

```bash
./packaging/generate-icons.sh
```

打包脚本会自动带上 `--icon`（文件不存在时跳过并提示）。

---

## 快速开始（推荐脚本）

| 平台 | 脚本 | 说明 |
|------|------|------|
| macOS | `./package-mac.sh` | 本地打 `.dmg` |
| macOS | `./package-mac.sh --app-image` | 仅生成 `.app` |
| Windows | `bash package-win.sh` 或 `.\package-win.ps1` | 本地打 `.exe` |
| Windows 便携 | `bash package-win.sh --app-image` | 不需要 WiX |
| macOS → Windows | `./package-win.sh --bundle` | 生成本地源码 zip，拷贝到 Windows 后打包 |

> **重要**：`jpackage` 不支持交叉编译，Mac 无法直接打 Windows exe。脚本**不会上传代码到 GitHub**；请用 `--bundle` 或虚机/局域网把源码拷到 Windows 本机打包。

---

## 环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 21+（完整 JDK，非 JRE） | 需包含 `jpackage`、`jlink`；设置 `JAVA_HOME` |
| Maven | 3.8+ | 编译与 `javafx:jlink` |
| WiX Toolset | 3.14+ | **仅 Windows** 打 `exe` / `msi` 时需要 |

验证环境：

```bash
java -version
mvn -version
jpackage --version
```

Windows 安装 WiX 后确认：

```powershell
candle.exe -?
```

---

## 通用变量（可按需修改）

```bash
APP_NAME="AiCode"
APP_VERSION="0.1.0"
MAIN_JAR="ai-codeing-mini-agent-0.1.0.jar"
MAIN_CLASS="com.aicode.app.AiCodeMain"
DIST_DIR="dist"
```

---

## 第一步：编译项目

在项目根目录执行：

```bash
mvn clean package -DskipTests
```

产物：`target/ai-codeing-mini-agent-0.1.0.jar`

---

## 第二步：准备 jpackage 输入

本项目**非模块化**（无 `module-info.java`），不能使用 `javafx:jlink`。打包脚本会：

1. 编译 fat jar（JavaFX 除外，因其含平台原生库）
2. 复制 `org.openjfx` 依赖到 `target/jpackage-input/lib`
3. 用 `jpackage --module-path ... --add-modules ...` 内嵌 JRE（需包含 `java.logging` 等，OkHttp 依赖）

> pom 版本为 `0.1.0` 时，jpackage 会自动映射为 `1.1.0`（macOS 要求 app-version 首位不能为 0）。

---

## macOS：打 `.dmg` 安装包

在 **macOS** 上，完成上述两步后执行：

```bash
mkdir -p dist

jpackage \
  --type dmg \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --dest dist \
  --runtime-image target/image \
  --input target \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --java-options "-Xmx1g"
```

**输出**：`dist/AiCode-0.1.0.dmg`（文件名以 `jpackage` 实际生成为准）

安装后从「应用程序」或 Launchpad 启动。配置文件 `aicode.yaml` 写在**启动时的工作目录**（与 `start.sh` 行为一致）。

### 可选：仅生成 `.app`（不打 dmg，便于调试）

```bash
jpackage \
  --type app-image \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --dest dist \
  --runtime-image target/image \
  --input target \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --java-options "-Xmx1g"
```

产物目录：`dist/AiCode.app`

---

## Windows 打包流程

> **jpackage 不支持交叉编译**：必须在 **Windows 本机** 执行（Git Bash 或 PowerShell）。Mac 上请先用 `./package-win.sh --bundle` 生成源码 zip 拷过去。

### 前置条件

| 工具 | 说明 |
|------|------|
| JDK 21+（完整版） | 含 `jpackage` |
| Maven 3.8+ | 编译 |
| Git Bash 或 PowerShell | 运行脚本 |
| WiX 3.14+ | 仅 `exe` 安装包需要；`--app-image` 不需要 |

### 方式 A：Mac 准备源码 → Windows 打包

在 **Mac** 上：

```bash
./package-win.sh --bundle
# 产出 dist/aiCodeMini-windows-build.zip
```

将 zip 拷到 Windows，解压后：

```bash
# Git Bash（推荐先试便携版，不需要 WiX）
bash package-win.sh --app-image

# 打 exe 安装包
bash package-win.sh
```

或 **PowerShell**（无需 Git Bash）：

```powershell
.\package-win.ps1 -AppImage    # 便携版
.\package-win.ps1              # exe 安装包
```

### 方式 B：Parallels / UTM 虚机

共享 Mac 上的项目文件夹，在 Windows 虚机内直接运行上述命令（**不要**复用 Mac 上编译的 `target/`，JavaFX 平台库不同）。

### 产出

| 命令 | 产出 |
|------|------|
| `bash package-win.sh` | `dist/AiCode-1.1.0.exe` |
| `bash package-win.sh --app-image` | `dist/AiCode/AiCode.exe` |

配置默认在 `%USERPROFILE%\aicode.yaml`，可在应用内切换工作区。

### 与 macOS 脚本的一致性

Windows 脚本已与 `package-mac.sh` 对齐：

- 不用 `javafx:jlink`（项目非模块化）
- 复制 `org.openjfx` 到 `target/jpackage-input/lib`（必须是 **win** 版 jar）
- `--add-modules` 含 `java.logging` 等（避免 OkHttp 闪退）
- `0.1.0` 自动映射为 `1.1.0`

---

## 一键脚本示例

### macOS（bash）

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

APP_NAME="AiCode"
APP_VERSION="0.1.0"
MAIN_JAR="ai-codeing-mini-agent-0.1.0.jar"
MAIN_CLASS="com.aicode.app.AiCodeMain"

mvn clean package -DskipTests
mvn javafx:jlink
mkdir -p dist

jpackage \
  --type dmg \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --dest dist \
  --runtime-image target/image \
  --input target \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --java-options "-Xmx1g"

echo "完成: dist/${APP_NAME}-${APP_VERSION}.dmg"
```

### Windows（PowerShell）

```powershell
Set-Location $PSScriptRoot

$APP_NAME = "AiCode"
$APP_VERSION = "0.1.0"
$MAIN_JAR = "ai-codeing-mini-agent-0.1.0.jar"
$MAIN_CLASS = "com.aicode.app.AiCodeMain"

mvn clean package -DskipTests
mvn javafx:jlink
New-Item -ItemType Directory -Force -Path dist | Out-Null

jpackage `
  --type exe `
  --name $APP_NAME `
  --app-version $APP_VERSION `
  --dest dist `
  --runtime-image target/image `
  --input target `
  --main-jar $MAIN_JAR `
  --main-class $MAIN_CLASS `
  --win-dir-chooser `
  --win-menu `
  --win-shortcut `
  --java-options "-Xmx1g"

Write-Host "完成: dist\$APP_NAME-$APP_VERSION.exe"
```

---

## 配置与使用

1. 首次使用前，在要作为「工作区」的目录下创建 `aicode.yaml`（可参考项目根目录的示例文件）。
2. 桌面版默认启动 JavaFX 图形界面；命令行模式需在终端用安装包自带的可执行文件加 `--cli` 参数（开发阶段可用 `./start.sh --cli`）。
3. 应用读写的是**当前工作目录**下的 `aicode.yaml`，不是安装目录内的文件。

---

## 常见问题

### `jpackage: command not found`

未使用完整 JDK，或 `JAVA_HOME/bin` 未加入 `PATH`。请安装 JDK 21+ 并导出：

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```

### macOS 提示「无法验证开发者」

未签名的应用会被 Gatekeeper 拦截。开发测试可在「系统设置 → 隐私与安全性」中允许，或使用 `codesign` / Apple 开发者证书签名（发布给外部用户时建议签名并公证）。

### Windows 打 `exe` 报错缺少 WiX

安装 [WiX Toolset 3.14+](https://wixtoolset.org/)，或将 `--type exe` 改为 `--type app-image` 生成本地目录版。

### 启动后 JavaFX 相关报错

确保在**目标操作系统**上执行完整打包流程（Mac 打 Mac、Windows 打 Windows）。不要在 Mac 上 jlink/jpackage 后再把产物拷到 Windows 使用。

### 打包后双击闪退 / 卡死（终端可运行）

**与根目录 `user.dir` 有关**，分两个阶段：

| 阶段 | 现象 | 原因 |
|------|------|------|
| 修复前 | 闪退 | Finder 启动时 `user.dir=/`，`Path.getFileName()` 对 `/` 返回 **null**，代码直接 `.toString()` → **NPE**，JavaFX 启动失败 |
| 修复后（仅回退到 `~`） | 卡死 | 工作区变成整个用户主目录，`initialize()` 在 **UI 线程** **递归扫描** `~/Library` 等目录（文件极多），界面长时间无响应 |

**为什么 null 不能「照常启动」？**  
不是 Java 不允许 null 路径，而是业务代码未做判空：`root.getFileName().toString()` 在根路径上必然 NPE。即使加了判空避免崩溃，把 `/` 或 `~` 当 IDE 工作区仍会扫描海量文件，一样会卡死。

**当前行为**：
- 配置目录：`user.dir` 从 `/` 回退到 `~`（读写 `~/aicode.yaml`）
- 默认工作区：`~/Documents`（不是整个 `~` 或 `/`）
- 文件树：**懒加载** + 后台线程，只在展开目录时读取子项

调试（模拟安装后从 Finder 启动）：

```bash
cd / && /Applications/AiCode.app/Contents/MacOS/AiCode
```

重新打包后再安装验证。

### 打包后双击闪退（缺少 JDK 模块）

在终端运行可执行文件查看完整堆栈：

```bash
dist/AiCode.app/Contents/MacOS/AiCode
```

常见原因：`jpackage --add-modules` 只打包了 JavaFX 模块，裁剪掉了 `java.logging` 等 JDK 模块，OkHttp 初始化时会 `ClassNotFoundException: java.util.logging.Logger`。打包脚本已加入 `java.logging,java.desktop,jdk.crypto.ec` 等模块；若仍缺模块，按终端报错继续追加到 `RUNTIME_MODULES`。

---
