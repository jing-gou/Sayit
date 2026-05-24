# 快速开始 - 语音输入法调试

## 🚀 一键调试

我为你创建了多个调试脚本，选择最适合你的：

### 方案1: Windows 批处理文件（推荐）
双击运行 `RUN_AND_DEBUG.bat`，它会自动：
1. 检查设备连接
2. 构建 APK
3. 安装到设备
4. 启动日志查看器

### 方案2: PowerShell 脚本
在 PowerShell 中运行：
```powershell
cd F:\Sayit\SayitVoiceIME
.\Start-Debug.ps1
```

### 方案3: 手动执行（适合调试）
如果你想手动控制每一步：

```bash
# 1. 设置环境变量
set JAVA_HOME=C:\Users\sugar\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64
set PATH=%JAVA_HOME%\bin;%PATH%

# 2. 构建
cd F:\Sayit\SayitVoiceIME
gradlew assembleDebug

# 3. 安装
"C:\Users\sugar\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk

# 4. 查看日志
"C:\Users\sugar\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -s VoiceKeyboard:V
```

## 📋 常见问题解决

### Q: 设备未找到
**A**: 确保：
- 设备已通过 USB 连接
- 设备已启用开发者选项
- 设备已启用 USB 调试
- 设备上已授权此电脑的调试权限

### Q: 安装失败
**A**: 确保：
- 设备未连接其他开发工具
- 设备存储空间足够
- 如果已安装，可能需要先卸载旧版本

### Q: 日志无输出
**A**: 确保：
- 应用已启动并点击了录音按钮
- 使用正确的日志标签：`VoiceKeyboard:V`
- 设备未连接其他日志查看工具

## 🔍 预期的日志输出

当你点击"🎤 开始录音"按钮后，应该看到类似：

```
D/VoiceKeyboard: 麦克风按钮被点击
D/VoiceKeyboard: 切换录音状态，当前状态: false
D/VoiceKeyboard: 开始语音识别被调用
D/VoiceKeyboard: 权限检查通过
D/VoiceKeyboard: WebSocket 客户端初始化完成
D/VoiceKeyboard: 开始连接 WebSocket...
D/VoiceKeyboard: URL: wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async
D/VoiceKeyboard: 开始初始化录音...
D/VoiceKeyboard: 缓冲区大小: 4096
D/VoiceKeyboard: ✅ 开始录音
D/VoiceKeyboard: ✅ WebSocket 连接成功
D/VoiceKeyboard: 响应码: 200
```

如果看到错误，比如：
```
E/VoiceKeyboard: ❌ 连接失败
E/VoiceKeyboard: 响应: 401 - Unauthorized
```

那么就是 API 认证问题，需要检查配置。

## 📞 获取帮助

如果还是有问题，请：
1. 运行调试脚本
2. 复制完整的日志输出
3. 告诉我具体的错误信息

我会帮你分析问题！