@echo off
chcp 65001 >nul
echo.
echo ========================================
echo  语音输入法 - 快速测试
echo ========================================
echo.

set ADB="C:\Users\sugar\AppData\Local\Android\Sdk\platform-tools\adb.exe"

echo [步骤 1] 检查设备连接...
%ADB% devices
echo.

if errorlevel 1 (
    echo ❌ 未找到设备，请确保：
    echo    - 设备已通过 USB 连接
    echo    - 已启用开发者选项
    echo    - 已启用 USB 调试
    echo.
    pause
    exit /b 1
)

echo ✅ 设备已连接
echo.

echo [步骤 2] 清理并重新构建...
cd /d "F:\Sayit\SayitVoiceIME"
set JAVA_HOME=C:\Users\sugar\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64
set PATH=%JAVA_HOME%\bin;%PATH%

call gradlew clean
if errorlevel 1 (
    echo ❌ 清理失败
    pause
    exit /b 1
)

call gradlew assembleDebug
if errorlevel 1 (
    echo ❌ 构建失败
    pause
    exit /b 1
)
echo ✅ 构建成功
echo.


echo [步骤 4] 安装新版本...
%ADB% install app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo ❌ 安装失败
    pause
    exit /b 1
)
echo ✅ 安装成功
echo.

echo ========================================
echo  准备查看日志
echo ========================================
echo.
echo 请在设备上：
echo   1. 打开设置 → 语言和输入法
echo   2. 启用 SayitVoiceIME 输入法
echo   3. 打开任意输入框
echo   4. 切换到语音输入法
echo   5. 点击 "🎤 开始录音" 按钮
echo.
echo 日志将在此窗口实时显示...
echo.
echo ========================================
echo.
echo 等待日志输出... (按 Ctrl+C 停止)
echo.

%ADB% logcat -s VoiceKeyboard:V

pause