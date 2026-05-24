# 语音输入法调试脚本

Write-Host "========================================" -ForegroundColor Green
Write-Host "  语音输入法 - 调试工具" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

$adb = "C:\Users\sugar\AppData\Local\Android\Sdk\platform-tools\adb.exe"

# 检查 adb 是否存在
if (-not (Test-Path $adb)) {
    Write-Host "❌ ADB 未找到，请检查路径" -ForegroundColor Red
    exit 1
}

# 检查设备连接
Write-Host "[1/2] 检查设备连接..." -ForegroundColor Yellow
& $adb devices
Write-Host ""

# 构建并安装
Write-Host "[2/2] 构建并安装..." -ForegroundColor Yellow
cd "F:\Sayit\SayitVoiceIME"

# 设置环境变量
$env:JAVA_HOME = "C:\Users\sugar\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 构建
Write-Host "正在构建..." -ForegroundColor Cyan
& .\gradlew assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 构建失败" -ForegroundColor Red
    exit 1
}

# 安装
Write-Host "正在安装..." -ForegroundColor Cyan
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 安装失败" -ForegroundColor Red
    exit 1
}

Write-Host "✅ 安装成功" -ForegroundColor Green
Write-Host ""

# 启动日志查看
Write-Host "========================================" -ForegroundColor Green
Write-Host "  日志查看器已启动" -ForegroundColor Green
Write-Host "  请在设备上测试语音输入法" -ForegroundColor Green
Write-Host "  按 Ctrl+C 停止查看日志" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "关键日志标签: VoiceKeyboard" -ForegroundColor Yellow
Write-Host ""

# 查看日志
& $adb logcat -s VoiceKeyboard:V