@echo off
echo ========================================
echo  语音输入法日志查看器
echo ========================================
echo.
echo 正在启动 adb logcat...
echo 请确保设备已连接并启用开发者模式
echo.
echo 按 Ctrl+C 停止查看日志
echo.

"C:\Users\sugar\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -s VoiceKeyboard:V

pause