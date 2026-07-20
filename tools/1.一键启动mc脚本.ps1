param(
    [Parameter(Position = 0)]
    [string]$Loader = "neoforge"
)

$loaderName = switch ($Loader) {
    { $_ -in "neoforge", "--neoforge" } { "neoforge"; break }
    { $_ -in "fabric", "--fabric" } { "fabric"; break }
    default {
        throw "不支持的加载器 '$Loader'。请使用 neoforge、--neoforge、fabric 或 --fabric。"
    }
}

# 启动 ./.githooks
git config core.hooksPath .githooks

# 设置环境变量
Set-Location "$PSScriptRoot\.."
$env:JAVA_HOME="C:\Software\Deps\Java\jdk-25.0.3"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

# 运行开发客户端
.\gradlew.bat ":${loaderName}:runClient"
