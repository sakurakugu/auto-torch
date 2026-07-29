param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$task = "neoforge"
$javaPath = $null
$javaRoot = "C:\Software\Deps\Java"

for ($index = 0; $index -lt $Arguments.Count; $index++) {
    switch ($Arguments[$index]) {
        "--build" { $task = "build" }
        "--neoforge" { $task = "neoforge" }
        "--forge" { $task = "forge" }
        "--fabric" { $task = "fabric" }
        "--path" {
            if ($index + 1 -ge $Arguments.Count) {
                throw "参数 --path 缺少 Java 路径。"
            }

            $index++
            $javaPath = $Arguments[$index]
        }
        default {
            throw "不支持的参数 '$($Arguments[$index])'。请使用 --build、--neoforge、--forge、--fabric 或 --path。"
        }
    }
}

Set-Location "$PSScriptRoot\.."

function Get-JavaInstallation {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JavaHome
    )

    $javaExecutable = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
        return $null
    }

    $versionOutput = (& $javaExecutable -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        return $null
    }

    if ($versionOutput -match 'version\s+"1\.(\d+)') {
        $majorVersion = [int]$Matches[1]
    } elseif ($versionOutput -match 'version\s+"(\d+)') {
        $majorVersion = [int]$Matches[1]
    } else {
        return $null
    }

    [PSCustomObject]@{
        Home = [System.IO.Path]::GetFullPath($JavaHome)
        MajorVersion = $majorVersion
    }
}

$javaInstallations = @()
if (Test-Path -LiteralPath $javaRoot -PathType Container) {
    $javaInstallations = @(Get-ChildItem -LiteralPath $javaRoot -Directory | ForEach-Object {
        Get-JavaInstallation -JavaHome $_.FullName
    } | Where-Object { $null -ne $_ })
}

if ($null -eq $javaPath) {
    $buildFile = Join-Path (Get-Location) "build.gradle"
    $buildContent = Get-Content -LiteralPath $buildFile -Raw
    if ($buildContent -notmatch 'JavaLanguageVersion\.of\(\s*(\d+)\s*\)') {
        throw "无法从 build.gradle 识别 Java toolchain 版本。"
    }

    $toolchainVersion = [int]$Matches[1]

    # Fabric Loom Remap 需要 Java 21 启动 Gradle，编译仍使用项目声明的 toolchain。
    if ($toolchainVersion -ge 21) {
        $gradleJavaVersion = $toolchainVersion
    } elseif ($buildContent -match 'fabric-loom-remap') {
        $gradleJavaVersion = 21
    } else {
        $gradleJavaVersion = 17
    }

    $selectedJava = $javaInstallations |
        Where-Object { $_.MajorVersion -eq $gradleJavaVersion } |
        Sort-Object Home -Descending |
        Select-Object -First 1

    if ($null -eq $selectedJava) {
        throw "当前版本需要 Java $gradleJavaVersion 启动 Gradle，但在 '$javaRoot' 中未找到对应 JDK。可使用 --path 指定 Java 路径。"
    }

    $javaPath = $selectedJava.Home
}

$javaHome = [System.IO.Path]::GetFullPath($javaPath)
if (-not (Test-Path -LiteralPath "$javaHome\bin\java.exe" -PathType Leaf)) {
    throw "指定的 Java 路径无效：'$javaPath'。未找到 bin\java.exe。"
}

# 启动 ./.githooks
git config core.hooksPath .githooks

# 设置环境变量
$env:JAVA_HOME=$javaHome
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$javaInstallationPaths = (@($javaInstallations.Home) + $javaHome | Select-Object -Unique) -join ','

Write-Host "使用 Java：$javaHome"

if ($task -eq "build") {
    # 先清理跨版本残留的映射和编译输出，再测试并构建所有加载器。
    .\gradlew.bat clean build "-Porg.gradle.java.installations.paths=$javaInstallationPaths"
} else {
    # 运行对应加载器的开发客户端。
    .\gradlew.bat ":${task}:runClient" "-Porg.gradle.java.installations.paths=$javaInstallationPaths"
}

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
