param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$task = "all"
$build = $false
$showHelp = $false
$mode = "debug"
$releasePath = $null
$javaPath = $null

for ($index = 0; $index -lt $Arguments.Count; $index++) {
    switch ($Arguments[$index]) {
        "--all" { $task = "all" }
        "--all_branch" { $task = "all_branch" }
        "--build" { $build = $true }
        "--help" { $showHelp = $true }
        "--neoforge" { $task = "neoforge" }
        "--forge" { $task = "forge" }
        "--fabric" { $task = "fabric" }
        "--debug" { $mode = "debug" }
        "--release" { $mode = "release" }
        "--path" {
            if ($index + 1 -ge $Arguments.Count) {
                throw "参数 --path 缺少生产测试端路径。"
            }

            $index++
            $releasePath = $Arguments[$index]
        }
        "--java-path" {
            if ($index + 1 -ge $Arguments.Count) {
                throw "参数 --java-path 缺少 Java 路径。"
            }

            $index++
            $javaPath = $Arguments[$index]
        }
        default {
            throw "不支持的参数 '$($Arguments[$index])'。请使用 --help 查看用法。"
        }
    }
}

function Show-Help {
    @"
用法：
  .\tools\1.一键启动mc脚本.ps1 [目标] [动作] [选项]

目标（默认 --all）：
  --all                 当前版本启动或构建全部加载器
  --neoforge             当前版本仅操作 NeoForge
  --fabric               当前版本仅操作 Fabric
  --forge                当前版本仅操作 Forge
  --all_branch           遍历所有 mc/<版本号> 分支

动作：
  --debug                启动开发客户端（默认）
  --build                执行构建，不启动客户端（可与任意目标组合）
  --release              构建 release、部署到生产测试端并启动（可与任意目标组合）

选项：
  --path <路径>          指定生产测试端目录（仅 --release）
  --java-path <路径>     指定 Java 安装目录
  --help                 显示此帮助

未提供参数时显示此帮助。
"@ | Write-Host
}

if ($Arguments.Count -eq 0 -or $showHelp) {
    Show-Help
    exit 0
}

$projectRoot = [System.IO.Path]::GetFullPath("$PSScriptRoot\..")
$worktreesRoot = Join-Path (Split-Path -Parent $projectRoot) "worktrees"
$defaultReleaseRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot "..\..\test-mc"))

function Set-JavaEnvironment {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        Write-Host "Gradle 将通过 Toolchain 自动检测或下载所需 Java。"
        return
    }

    $javaHome = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath "$javaHome\bin\java.exe" -PathType Leaf)) {
        throw "指定的 Java 路径无效：'$Path'。未找到 bin\java.exe。"
    }

    $env:JAVA_HOME = $javaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

function Get-VersionSortKey {
    param([string]$Version)

    $parts = @($Version.Split('.') | ForEach-Object { [int]$_ })
    while ($parts.Count -lt 4) {
        $parts += 0
    }

    return "{0:D5}.{1:D5}.{2:D5}.{3:D5}" -f $parts[0], $parts[1], $parts[2], $parts[3]
}

function Get-GradleProperty {
    param(
        [string]$Root,
        [string]$Name
    )

    $propertiesPath = Join-Path $Root "gradle.properties"
    foreach ($line in Get-Content -LiteralPath $propertiesPath) {
        if ($line -match "^$([regex]::Escape($Name))=(.*)$") {
            return $Matches[1].Trim()
        }
    }

    throw "未在 $propertiesPath 中找到属性 $Name。"
}

function Get-MinecraftBranches {
    $branches = & git -C $projectRoot for-each-ref --format="%(refname:short)" refs/heads/mc/
    if ($LASTEXITCODE -ne 0) {
        throw "无法读取 Minecraft 版本分支。"
    }

    return @(
        $branches |
            ForEach-Object {
                if ($_ -match "^mc/(\d+(?:\.\d+){1,3})$") {
                    [PSCustomObject]@{
                        Branch = $_
                        Version = $Matches[1]
                        SortKey = Get-VersionSortKey $Matches[1]
                    }
                }
            } |
            Where-Object { $null -ne $_ } |
            Sort-Object SortKey -Descending
    )
}

function Set-ClientLanguage {
    param(
        [string]$WorktreeRoot,
        [string]$Loader
    )

    $propertiesPath = Join-Path $WorktreeRoot "gradle.properties"
    $minecraftVersion = $null
    if (Test-Path -LiteralPath $propertiesPath -PathType Leaf) {
        $versionLine = Get-Content -LiteralPath $propertiesPath | Where-Object { $_ -match "^minecraft_version=(.+)$" } | Select-Object -First 1
        if ($null -ne $versionLine) {
            $minecraftVersion = $Matches[1]
        }
    }

    # 1.12.2 及更早版本使用旧的语言标识。
    $language = "zh_cn"
    if ($minecraftVersion -match "^1\.(\d+)(?:\.|$)" -and [int]$Matches[1] -le 12) {
        $language = "zh_CN"
    }

    $runDirectory = Join-Path $WorktreeRoot "$Loader\run"
    $optionsPath = Join-Path $runDirectory "options.txt"
    New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

    $options = if (Test-Path -LiteralPath $optionsPath -PathType Leaf) {
        @(Get-Content -LiteralPath $optionsPath)
    } else {
        @()
    }

    $updatedOptions = @()
    $languageSet = $false
    foreach ($option in $options) {
        if ($option -match "^lang:") {
            if (-not $languageSet) {
                $updatedOptions += "lang:$language"
                $languageSet = $true
            }
            continue
        }

        $updatedOptions += $option
    }

    if (-not $languageSet) {
        $updatedOptions += "lang:$language"
    }

    Set-Content -LiteralPath $optionsPath -Value $updatedOptions -Encoding ascii
}

function Get-VersionWorktree {
    param(
        [string]$Branch,
        [string]$Version
    )

    $versionRoot = Join-Path $worktreesRoot $Version
    if (Test-Path -LiteralPath "$versionRoot\.git") {
        return $versionRoot
    }

    if (Test-Path -LiteralPath $versionRoot -PathType Container) {
        if ((Get-ChildItem -LiteralPath $versionRoot -Force).Count -gt 0) {
            throw "版本工作树目录已存在但不是 Git 工作树：$versionRoot"
        }
    }

    New-Item -ItemType Directory -Path $worktreesRoot -Force | Out-Null
    & git -C $projectRoot worktree prune | Out-Host
    Write-Host "创建 $Version 工作树：$versionRoot"
    & git -C $projectRoot worktree add $versionRoot $Branch | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "无法创建 $Version 工作树。"
    }

    return $versionRoot
}

function Invoke-LoaderClient {
    param(
        [string]$WorktreeRoot,
        [string]$Loader,
        [string]$Version
    )

    if (-not (Test-Path -LiteralPath (Join-Path $WorktreeRoot "$Loader\build.gradle") -PathType Leaf)) {
        Write-Host "[$Version] 未找到 $Loader，跳过。"
        return
    }

    Write-Host "[$Version] 启动 $Loader；关闭游戏后继续。"
    Set-ClientLanguage $WorktreeRoot $Loader
    Push-Location $WorktreeRoot
    try {
        & .\gradlew.bat ":${Loader}:runClient"
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "[$Version] $Loader 已退出，退出码：$LASTEXITCODE。继续下一个加载器。"
        }
    } finally {
        Pop-Location
    }
}

function Invoke-ReleaseBuild {
    param(
        [string]$Root,
        [string]$Version
    )

    Write-Host "`n========== 打包 Minecraft $Version =========="
    & git -C $Root config core.hooksPath .githooks
    Push-Location $Root
    try {
        & .\gradlew.bat clean build | Out-Host
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($exitCode -ne 0) {
        throw "[$Version] 打包失败，退出码：$exitCode。"
    }

    $modVersion = Get-GradleProperty $Root "mod_version"
    $artifactRoot = Join-Path $Root "build\v$modVersion"
    $artifacts = @(Get-ChildItem -LiteralPath $artifactRoot -File -Filter "*.jar" -ErrorAction SilentlyContinue)
    if ($artifacts.Count -eq 0) {
        throw "[$Version] 未在 $artifactRoot 中找到打包产物。"
    }

    return $artifacts
}

function Get-ReleaseArtifacts {
    $modVersion = Get-GradleProperty $projectRoot "mod_version"
    $archiveRoot = Join-Path $projectRoot "build\v$modVersion"

    if ($task -ne "all_branch") {
        $minecraftVersion = Get-GradleProperty $projectRoot "minecraft_version"
        return @(Invoke-ReleaseBuild $projectRoot $minecraftVersion)
    }

    $versions = Get-MinecraftBranches
    if ($versions.Count -eq 0) {
        throw "未找到 mc/<版本号> 形式的本地分支。"
    }

    New-Item -ItemType Directory -Path $archiveRoot -Force | Out-Null
    Get-ChildItem -LiteralPath $archiveRoot -File -Filter "autotorch-v$modVersion-mc*.jar" -ErrorAction SilentlyContinue |
        Remove-Item -Force

    foreach ($version in $versions) {
        $versionRoot = Get-VersionWorktree $version.Branch $version.Version
        $branchModVersion = Get-GradleProperty $versionRoot "mod_version"
        if ($branchModVersion -ne $modVersion) {
            throw "[$($version.Version)] 模组版本是 $branchModVersion，无法汇总到 v$modVersion。"
        }

        foreach ($artifact in @(Invoke-ReleaseBuild $versionRoot $version.Version)) {
            Copy-Item -LiteralPath $artifact.FullName -Destination $archiveRoot -Force
        }
    }

    $artifacts = @(Get-ChildItem -LiteralPath $archiveRoot -File -Filter "autotorch-v$modVersion-mc*.jar")
    Write-Host "`n已将 $($artifacts.Count) 个产物汇总到：$archiveRoot"
    return $artifacts
}

function Get-ReleaseTargets {
    param(
        [System.IO.FileInfo[]]$Artifacts,
        [string]$ReleaseRoot,
        [string]$LoaderFilter
    )

    $versionsRoot = Join-Path $ReleaseRoot ".minecraft\versions"
    if (-not (Test-Path -LiteralPath $versionsRoot -PathType Container)) {
        throw "生产测试端缺少版本目录：$versionsRoot"
    }

    $loaderOrder = @{ forge = 0; fabric = 1; neoforge = 2 }
    $targets = foreach ($artifact in $Artifacts) {
        if ($artifact.Name -notmatch "^autotorch-v[^-]+-mc(?<version>\d+(?:\.\d+){1,3})-(?<loader>forge|fabric|neoforge)\.jar$") {
            Write-Warning "无法识别产物名称，跳过：$($artifact.Name)"
            continue
        }

        $minecraftVersion = $Matches.version
        $loader = $Matches.loader.ToLowerInvariant()
        if (-not [string]::IsNullOrWhiteSpace($LoaderFilter) -and $loader -ne $LoaderFilter) {
            continue
        }
        $instancePattern = "^$([regex]::Escape($minecraftVersion))-$([regex]::Escape($loader))(?:[_ ].*)?$"
        $instances = @(
            Get-ChildItem -LiteralPath $versionsRoot -Directory |
                Where-Object { $_.Name -match $instancePattern }
        )

        if ($instances.Count -eq 0) {
            throw "未安装 $minecraftVersion $loader 的生产测试实例；release 模式不会下载实例。"
        }
        if ($instances.Count -gt 1) {
            throw "$minecraftVersion $loader 匹配到多个生产测试实例：$($instances.Name -join '、')"
        }

        [PSCustomObject]@{
            MinecraftVersion = $minecraftVersion
            Loader = $loader
            LoaderOrder = $loaderOrder[$loader]
            SortKey = Get-VersionSortKey $minecraftVersion
            Artifact = $artifact.FullName
            InstanceName = $instances[0].Name
            InstanceRoot = $instances[0].FullName
        }
    }

    return @(
        $targets | Sort-Object `
            @{ Expression = "SortKey"; Descending = $true },
            @{ Expression = "LoaderOrder"; Descending = $false }
    )
}

function Install-ReleaseArtifact {
    param([PSCustomObject]$Target)

    $modsRoot = Join-Path $Target.InstanceRoot "mods"
    New-Item -ItemType Directory -Path $modsRoot -Force | Out-Null

    # 移除旧版，避免同一个实例同时加载多个 Auto Torch。
    Get-ChildItem -LiteralPath $modsRoot -File -Filter "autotorch-*.jar" -ErrorAction SilentlyContinue |
        Remove-Item -Force
    Copy-Item -LiteralPath $Target.Artifact -Destination $modsRoot -Force

    Write-Host "已部署 [$($Target.InstanceName)] $(Split-Path -Leaf $Target.Artifact)"
}

function Get-PclProcesses {
    param([string]$ReleaseRoot)

    $normalizedRoot = [System.IO.Path]::GetFullPath($ReleaseRoot).TrimEnd('\') + '\'
    return @(
        Get-CimInstance Win32_Process |
            Where-Object {
                -not [string]::IsNullOrWhiteSpace($_.ExecutablePath) -and
                $_.ExecutablePath.StartsWith($normalizedRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
                [System.IO.Path]::GetFileName($_.ExecutablePath) -in @("PCL启动器.exe", "Plain Craft Launcher 2.exe")
            }
    )
}

function Stop-PclProcesses {
    param([string]$ReleaseRoot)

    foreach ($processInfo in @(Get-PclProcesses $ReleaseRoot)) {
        $process = Get-Process -Id $processInfo.ProcessId -ErrorAction SilentlyContinue
        if ($null -ne $process -and $process.MainWindowHandle -ne 0) {
            $null = $process.CloseMainWindow()
        }
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    do {
        $remaining = @(Get-PclProcesses $ReleaseRoot)
        if ($remaining.Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)

    # PCL 启动后会隐藏窗口，只结束当前测试端目录中的残留 PCL。
    foreach ($processInfo in @(Get-PclProcesses $ReleaseRoot)) {
        Stop-Process -Id $processInfo.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

function Set-PclSelectedVersion {
    param(
        [string]$ReleaseRoot,
        [string]$InstanceName
    )

    $pclIniPath = Join-Path $ReleaseRoot ".minecraft\PCL.ini"
    $lines = if (Test-Path -LiteralPath $pclIniPath -PathType Leaf) {
        @(Get-Content -LiteralPath $pclIniPath)
    } else {
        @()
    }

    $updatedLines = @()
    $versionSet = $false
    foreach ($line in $lines) {
        if ($line -match "^Version:") {
            if (-not $versionSet) {
                $updatedLines += "Version:$InstanceName"
                $versionSet = $true
            }
            continue
        }
        $updatedLines += $line
    }
    if (-not $versionSet) {
        $updatedLines += "Version:$InstanceName"
    }

    Set-Content -LiteralPath $pclIniPath -Value $updatedLines -Encoding utf8
}

function Invoke-PclLaunchButton {
    param(
        [string]$ReleaseRoot,
        [int]$TimeoutSeconds = 60
    )

    Add-Type -AssemblyName UIAutomationClient
    Add-Type -AssemblyName UIAutomationTypes
    Add-Type -AssemblyName System.Windows.Forms
    if ($null -eq ("PclMouseInput" -as [type])) {
        Add-Type -TypeDefinition @"
using System.Runtime.InteropServices;

public static class PclMouseInput
{
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(System.IntPtr window, int command);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(System.IntPtr window);

    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, System.UIntPtr extraInfo);
}
"@
    }
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)

    do {
        foreach ($processInfo in @(Get-PclProcesses $ReleaseRoot)) {
            $process = Get-Process -Id $processInfo.ProcessId -ErrorAction SilentlyContinue
            if ($null -eq $process -or $process.MainWindowHandle -eq 0) {
                continue
            }

            try {
                [PclMouseInput]::ShowWindow($process.MainWindowHandle, 9) | Out-Null
                [PclMouseInput]::SetForegroundWindow($process.MainWindowHandle) | Out-Null
                $window = [System.Windows.Automation.AutomationElement]::FromHandle($process.MainWindowHandle)
                $condition = [System.Windows.Automation.PropertyCondition]::new(
                    [System.Windows.Automation.AutomationElement]::NameProperty,
                    "启动游戏"
                )
                $button = $window.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $condition)
                if ($null -eq $button -or -not $button.Current.IsEnabled) {
                    continue
                }

                # PCL 将“启动游戏”暴露为 TextBlock，不支持 InvokePattern，只能点击控件坐标。
                $bounds = $button.Current.BoundingRectangle
                if ($bounds.IsEmpty -or $bounds.Width -le 0 -or $bounds.Height -le 0) {
                    continue
                }

                $originalPosition = [System.Windows.Forms.Cursor]::Position
                try {
                    [System.Windows.Forms.Cursor]::Position = [System.Drawing.Point]::new(
                        [int]($bounds.Left + $bounds.Width / 2),
                        [int]($bounds.Top + $bounds.Height / 2)
                    )
                    [PclMouseInput]::mouse_event(0x0002, 0, 0, 0, [System.UIntPtr]::Zero)
                    [PclMouseInput]::mouse_event(0x0004, 0, 0, 0, [System.UIntPtr]::Zero)
                } finally {
                    [System.Windows.Forms.Cursor]::Position = $originalPosition
                }
                return
            } catch {
                # PCL 刷新窗口时控件可能短暂失效，继续重试。
            }
        }

        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)

    throw '等待 PCL 的“启动游戏”按钮超时。'
}

function Wait-MinecraftProcessStart {
    param(
        [string]$InstanceRoot,
        [int]$TimeoutSeconds = 180
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $gameProcess = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
            Where-Object {
                -not [string]::IsNullOrWhiteSpace($_.CommandLine) -and
                $_.CommandLine.IndexOf($InstanceRoot, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
            } |
            Select-Object -First 1

        if ($null -ne $gameProcess) {
            return $gameProcess.ProcessId
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "等待 Minecraft 进程启动超时：$InstanceRoot"
}

function Start-ReleaseClient {
    param(
        [PSCustomObject]$Target,
        [string]$ReleaseRoot
    )

    Write-Host "`n[$($Target.InstanceName)] 启动生产客户端；关闭游戏后继续。"
    Set-PclSelectedVersion $ReleaseRoot $Target.InstanceName

    try {
        Start-Process -FilePath (Join-Path $ReleaseRoot "PCL启动器.exe") -WorkingDirectory $ReleaseRoot | Out-Null
        Invoke-PclLaunchButton $ReleaseRoot
        $gameProcessId = Wait-MinecraftProcessStart $Target.InstanceRoot
        Write-Host "[$($Target.InstanceName)] Minecraft 已启动。"
        Wait-Process -Id $gameProcessId -ErrorAction SilentlyContinue
    } finally {
        Stop-PclProcesses $ReleaseRoot
    }
}

Set-JavaEnvironment $javaPath

if ($mode -eq "release") {
    $releaseRoot = if ([string]::IsNullOrWhiteSpace($releasePath)) {
        $defaultReleaseRoot
    } else {
        [System.IO.Path]::GetFullPath($releasePath)
    }
    if (-not (Test-Path -LiteralPath (Join-Path $releaseRoot "PCL启动器.exe") -PathType Leaf)) {
        throw "生产测试端路径无效：$releaseRoot。未找到 PCL启动器.exe。"
    }
    if ((Get-PclProcesses $releaseRoot).Count -gt 0) {
        throw "生产测试端的 PCL 正在运行，请先关闭后再执行 release。"
    }

    $artifacts = @(Get-ReleaseArtifacts)
    $loaderFilter = if ($task -in @("forge", "fabric", "neoforge")) { $task } else { $null }
    $targets = @(Get-ReleaseTargets $artifacts $releaseRoot $loaderFilter)
    if ($targets.Count -eq 0) {
        throw "没有可部署的 release 产物。"
    }

    foreach ($target in $targets) {
        Install-ReleaseArtifact $target
    }
    foreach ($target in $targets) {
        Start-ReleaseClient $target $releaseRoot
    }
    exit 0
}

if (-not [string]::IsNullOrWhiteSpace($releasePath)) {
    Write-Warning "--path 仅在 --release 模式下生效，当前将忽略该参数。"
}

if ($build) {
    if ($task -eq "all_branch") {
        $versions = Get-MinecraftBranches
        if ($versions.Count -eq 0) {
            throw "未找到 mc/<版本号> 形式的本地分支。"
        }

        foreach ($version in $versions) {
            $versionRoot = Get-VersionWorktree $version.Branch $version.Version
            Write-Host "`n========== 构建 Minecraft $($version.Version) =========="
            Push-Location $versionRoot
            try {
                & .\gradlew.bat clean build
                if ($LASTEXITCODE -ne 0) {
                    throw "[$($version.Version)] 构建失败，退出码：$LASTEXITCODE。"
                }
            } finally {
                Pop-Location
            }
        }
        exit 0
    }

    Set-Location $projectRoot
    git config core.hooksPath .githooks
    if ($task -eq "all") {
        .\gradlew.bat clean build
    } else {
        .\gradlew.bat clean ":${task}:build"
    }
    exit $LASTEXITCODE
}

if ($task -eq "all") {
    Set-Location $projectRoot
    git config core.hooksPath .githooks
    foreach ($loader in @("forge", "fabric", "neoforge")) {
        if (-not (Test-Path -LiteralPath (Join-Path $projectRoot "$loader\build.gradle") -PathType Leaf)) {
            Write-Host "当前版本未找到 $loader，跳过。"
            continue
        }

        Write-Host "启动 $loader；关闭游戏后继续。"
        Set-ClientLanguage $projectRoot $loader
        & .\gradlew.bat ":${loader}:runClient"
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "$loader 已退出，退出码：$LASTEXITCODE。继续下一个加载器。"
        }
    }
    exit 0
}

if ($task -ne "all_branch") {
    Set-Location $projectRoot
    git config core.hooksPath .githooks
    Set-ClientLanguage $projectRoot $task
    & .\gradlew.bat ":${task}:runClient"
    exit $LASTEXITCODE
}

$versions = Get-MinecraftBranches
if ($versions.Count -eq 0) {
    throw "未找到 mc/<版本号> 形式的本地分支。"
}

foreach ($version in $versions) {
    Write-Host "`n========== Minecraft $($version.Version) =========="
    $versionRoot = Get-VersionWorktree $version.Branch $version.Version

    git -C $versionRoot config core.hooksPath .githooks
    foreach ($loader in @("forge", "fabric", "neoforge")) {
        Invoke-LoaderClient $versionRoot $loader $version.Version
    }
}
