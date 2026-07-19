# 启动 ./.githooks
git config core.hooksPath .githooks

# 设置环境变量
Set-Location "$PSScriptRoot\.."
$env:JAVA_HOME="C:\Software\Deps\Java\jdk-25.0.3"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

# 运行开发客户端
.\gradlew.bat runClient