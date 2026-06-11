# Windows 安装包打包脚本（PowerShell，无需 Git Bash）
#
# 用法：
#   .\package-win.ps1              # exe 安装包（需 WiX）
#   .\package-win.ps1 -AppImage    # 便携目录（不需要 WiX，推荐先试）
#
# 依赖：JDK 21+、Maven 3.8+；exe 模式需 WiX 3.14+

param(
    [switch]$AppImage
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$APP_NAME       = "AiCode"
$MAIN_CLASS     = "com.aicode.app.AiCodeMain"
$DIST_DIR       = "dist"
$INPUT_DIR      = "target/jpackage-input"
$ICON_FILE      = Join-Path $PSScriptRoot "packaging/icons/aicode.ico"
$RUNTIME_MODULES = "javafx.controls,javafx.fxml,javafx.web,java.logging,java.desktop,jdk.crypto.ec"
$PACKAGE_TYPE   = if ($AppImage) { "app-image" } else { "exe" }
$WIX_URL        = "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
$TOOLS_DIR      = Join-Path $PSScriptRoot ".tools"
$WIX_DIR        = Join-Path $TOOLS_DIR "wix"

function Info($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Die($msg)  { Write-Host "错误: $msg" -ForegroundColor Red; exit 1 }

function Get-JPackageVersion([string]$version) {
    $major = $version.Split('.')[0]
    if ([string]::IsNullOrEmpty($major) -or $major -eq "0") {
        return "1." + ($version -replace '^0\.', '')
    }
    return $version
}

function Ensure-Java {
    if (-not $env:JAVA_HOME) {
        $candidates = @(
            "C:\Program Files\Java\jdk-21",
            "C:\Program Files\Eclipse Adoptium\jdk-21*",
            "C:\Program Files\Microsoft\jdk-21*"
        )
        foreach ($pattern in $candidates) {
            $found = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($found -and (Test-Path "$found\bin\java.exe")) {
                $env:JAVA_HOME = $found.FullName
                break
            }
        }
    }
    if ($env:JAVA_HOME) {
        $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Die "未找到 java，请安装 JDK 21+" }
    if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) { Die "未找到 jpackage，请使用完整 JDK" }
    Info ("Java: " + (java -version 2>&1 | Select-Object -First 1))
}

function Ensure-Maven {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Die "未找到 mvn" }
}

function Ensure-WiX {
    $wixBins = @(
        $WIX_DIR,
        "${env:ProgramFiles(x86)}\WiX Toolset v3.14\bin",
        "$env:ProgramFiles\WiX Toolset v3.14\bin"
    )
    foreach ($dir in $wixBins) {
        if (Test-Path "$dir\candle.exe") {
            $env:PATH = "$dir;$env:PATH"
            Info "WiX: $dir"
            return
        }
    }
    Info "未检测到 WiX，尝试安装..."
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        winget install --id WiXToolset.WiXToolset -e --accept-package-agreements --accept-source-agreements 2>$null
        foreach ($dir in $wixBins) {
            if (Test-Path "$dir\candle.exe") {
                $env:PATH = "$dir;$env:PATH"
                return
            }
        }
    }
    if (Get-Command choco -ErrorAction SilentlyContinue) {
        choco install wixtoolset -y --no-progress
        foreach ($dir in $wixBins) {
            if (Test-Path "$dir\candle.exe") {
                $env:PATH = "$dir;$env:PATH"
                return
            }
        }
    }
    # 下载便携版
    Info "下载 WiX 便携版..."
    New-Item -ItemType Directory -Force -Path $WIX_DIR | Out-Null
    $zip = Join-Path $TOOLS_DIR "wix314-binaries.zip"
    Invoke-WebRequest -Uri $WIX_URL -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $WIX_DIR -Force
    $env:PATH = "$WIX_DIR;$env:PATH"
    if (-not (Get-Command candle -ErrorAction SilentlyContinue)) {
        Die "无法安装 WiX。请改用: .\package-win.ps1 -AppImage"
    }
}

function Prepare-JPackageInput([string]$mainJar) {
    if (Test-Path $INPUT_DIR) { Remove-Item -Recurse -Force $INPUT_DIR }
    New-Item -ItemType Directory -Force -Path "$INPUT_DIR\lib" | Out-Null
    Copy-Item "target\$mainJar" $INPUT_DIR

    Info "复制 JavaFX 依赖（Windows 版）..."
    mvn dependency:copy-dependencies `
        -DoutputDirectory="$INPUT_DIR/lib" `
        -DincludeScope=runtime `
        -DincludeGroupIds=org.openjfx `
        -q

    $macJars = Get-ChildItem "$INPUT_DIR\lib\javafx-*-mac.jar" -ErrorAction SilentlyContinue
    if ($macJars) { Die "检测到 macOS 版 JavaFX，请删除 target/ 后在 Windows 本机重新打包" }

    $count = (Get-ChildItem "$INPUT_DIR\lib\javafx-*.jar" -ErrorAction SilentlyContinue).Count
    if ($count -eq 0) { Die "未找到 JavaFX 依赖" }
    Info "JavaFX jar 数量: $count"
}

if ($env:WSL_DISTRO_NAME) {
    Die "检测到 WSL，请在 Windows PowerShell / CMD 中运行本脚本"
}

Ensure-Java
Ensure-Maven

$APP_VERSION = mvn help:evaluate -Dexpression=project.version -q -DforceStdout
$MAIN_JAR    = (mvn help:evaluate -Dexpression=project.build.finalName -q -DforceStdout) + ".jar"
$JPKG_VER    = Get-JPackageVersion $APP_VERSION

if ($PACKAGE_TYPE -eq "exe") { Ensure-WiX }

Info "应用: $APP_NAME $APP_VERSION → jpackage $JPKG_VER"
Info "打包类型: $PACKAGE_TYPE"

Info "编译项目..."
mvn clean package -DskipTests -q
if (-not (Test-Path "target\$MAIN_JAR")) { Die "未找到 target\$MAIN_JAR" }

Prepare-JPackageInput $MAIN_JAR
New-Item -ItemType Directory -Force -Path $DIST_DIR | Out-Null
Remove-Item -Recurse -Force "$DIST_DIR\$APP_NAME" -ErrorAction SilentlyContinue
Remove-Item -Force "$DIST_DIR\$APP_NAME-$JPKG_VER.exe" -ErrorAction SilentlyContinue

Info "执行 jpackage..."
$jpkgArgs = @(
    "--type", $PACKAGE_TYPE,
    "--name", $APP_NAME,
    "--app-version", $JPKG_VER,
    "--dest", $DIST_DIR,
    "--input", $INPUT_DIR,
    "--main-jar", $MAIN_JAR,
    "--main-class", $MAIN_CLASS,
    "--module-path", "$INPUT_DIR/lib",
    "--add-modules", $RUNTIME_MODULES,
    "--java-options", "-Xmx1g"
)
if ($PACKAGE_TYPE -eq "exe") {
    $jpkgArgs += @("--win-dir-chooser", "--win-menu", "--win-shortcut")
}
if (Test-Path $ICON_FILE) {
    $jpkgArgs += @("--icon", $ICON_FILE)
} else {
    Info "未找到 $ICON_FILE，跳过应用图标（可运行 packaging/generate-icons.sh）"
}
& jpackage @jpkgArgs

Write-Host ""
if ($PACKAGE_TYPE -eq "exe") {
    Info "完成！安装包: $DIST_DIR\$APP_NAME-$JPKG_VER.exe"
} else {
    Info "完成！运行: $DIST_DIR\$APP_NAME\$APP_NAME.exe"
}
