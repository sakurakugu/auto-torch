param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$task = "neoforge"
$javaPath = "C:\Software\Deps\Java\jdk-21"

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

# 启动 ./.githooks
git config core.hooksPath .githooks

# 设置环境变量
Set-Location "$PSScriptRoot\.."
$javaHome = [System.IO.Path]::GetFullPath($javaPath)
if (-not (Test-Path -LiteralPath "$javaHome\bin\java.exe" -PathType Leaf)) {
    throw "指定的 Java 路径无效：'$javaPath'。未找到 bin\java.exe。"
}
$env:JAVA_HOME=$javaHome
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
