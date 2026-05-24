@echo off
echo ========================================
echo  语音输入法 - 构建并调试
echo ========================================
echo.

set ADB="C:\Users\sugar\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set JAVA_HOME="C:\Users\sugar\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"

echo [1/4] 检查设备连接...
%ADB% devices
echo.

echo [2/4] 构建APK...
cd /d "F:\Sayit\SayitVoiceIME"
call gradlew assembleDebug
if errorlevel 1 (
    echo 构建失败！
    pause
    exit /b 1
)
echo ✅ 构建成功
echo.

echo [3/4] 安装APK...
%ADB% install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo 安装失败！
    pause
    exit /b 1
)
echo ✅ 安装成功
echo.

echo [4/4] 启动日志查看...
echo.
echo ========================================
echo  日志查看器已启动
echo  请在设备上测试语音输入法
echo  按 Ctrl+C 停止查看日志
echo ========================================
echo.
echo 关键日志标签：VoiceKeyboard
echo.
echo 等待日志输出...
%ADB% logcat -s VoiceKeyboard:V

pause