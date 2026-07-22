param(
    [Parameter(Position = 0)]
    [string]$Action = "--neoforge"
)

$task = switch ($Action) {
    "--build" { "build"; break }
    "--neoforge"  { "neoforge"; break }
    "--forge" { "forge"; break }
    "--fabric" { "fabric"; break }
    default {
        throw "不支持的操作 '$Action'。请使用 --build、--neoforge、--forge 或 --fabric。"
    }
}

# 启动 ./.githooks
git config core.hooksPath .githooks

# 设置环境变量
Set-Location "$PSScriptRoot\.."
$env:JAVA_HOME="C:\Software\Deps\Java\jdk-25.0.3"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

if ($task -eq "build") {
    # 测试并构建所有加载器，产物位于根目录 build 文件夹。
    .\gradlew.bat build
} else {
    # 运行对应加载器的开发客户端。
    .\gradlew.bat ":${task}:runClient"
}

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
