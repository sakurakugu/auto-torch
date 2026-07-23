param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9A-Za-z._-]+$')]
    [string]$MinecraftVersion
)

$ErrorActionPreference = 'Stop'

function Get-MavenVersions {
    param([Parameter(Mandatory = $true)][string]$Url)

    [xml]$metadata = (Invoke-WebRequest -UseBasicParsing $Url).Content
    return @($metadata.metadata.versioning.versions.version)
}

function Add-Candidates {
    param(
        [Parameter(Mandatory = $true)][System.Collections.Generic.List[object]]$Results,
        [Parameter(Mandatory = $true)][string]$Component,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Versions
    )

    if ($Versions.Count -eq 0) {
        $Results.Add([PSCustomObject]@{
            Component = $Component
            Version = '无匹配版本'
        })
        return
    }

    foreach ($version in ($Versions | Select-Object -Last 5)) {
        $Results.Add([PSCustomObject]@{
            Component = $Component
            Version = $version
        })
    }
}

$manifest = Invoke-RestMethod 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
$minecraft = $manifest.versions | Where-Object { $_.id -eq $MinecraftVersion } | Select-Object -First 1
if ($null -eq $minecraft) {
    throw "Mojang 版本清单中不存在 Minecraft $MinecraftVersion"
}

$forgeVersions = Get-MavenVersions 'https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml'
$neoForgeVersions = Get-MavenVersions 'https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml'
$fabricApiVersions = Get-MavenVersions 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml'
$fabricLoaderVersions = Get-MavenVersions 'https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml'

# NeoForge 在旧式 1.x 版本和年份版本中使用不同的版本前缀。
$neoForgePrefix = if ($MinecraftVersion -match '^1\.(\d+)(?:\.(\d+))?$') {
    $patch = if ($null -eq $Matches[2]) { '0' } else { $Matches[2] }
    "$($Matches[1]).$patch."
} elseif ($MinecraftVersion -match '^\d+\.\d+$') {
    "$MinecraftVersion.0."
} else {
    "$MinecraftVersion."
}

$results = [System.Collections.Generic.List[object]]::new()
$results.Add([PSCustomObject]@{ Component = "Minecraft ($($minecraft.type))"; Version = $MinecraftVersion })
Add-Candidates $results 'Forge' @($forgeVersions | Where-Object { $_ -like "$MinecraftVersion-*" })
Add-Candidates $results 'NeoForge' @($neoForgeVersions | Where-Object { $_ -like "$neoForgePrefix*" })
Add-Candidates $results 'Fabric API' @($fabricApiVersions | Where-Object { $_ -like "*+$MinecraftVersion" })
Add-Candidates $results 'Fabric Loader' @($fabricLoaderVersions)

$results | Format-Table -AutoSize

Write-Host ''
Write-Host '以上是官方元数据中的候选版本。采用前仍需核对兼容性并完成实际构建。'
