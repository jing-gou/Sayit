# 语音输入法调试指南

## 🔍 按下录音按钮没反应？请按以下步骤检查：

### 1. 权限检查
```bash
# 使用 ADB 查看权限状态
adb shell dumpsys package org.sayit.voiceime | grep permission
```

### 2. 查看日志
```bash
# 实时查看日志
adb logcat | grep VoiceKeyboard

# 或过滤所有相关日志
adb logcat | grep -E "VoiceKeyboard|VolcanoAuth|WebSocket"
```

### 3. 常见问题排查

#### 权限问题
- 症状：日志显示 "没有录音权限"
- 解决：系统设置 → 应用管理 → SayitVoiceIME → 权限 → 录音 → 允许

#### 网络问题
- 症状：日志显示 "连接超时" 或 "连接失败"
- 解决：确保设备有网络连接，检查防火墙设置

#### API密钥问题
- 症状：日志显示 "认证失败，请检查API密钥"
- 解决：检查 local.properties 中的 API 配置是否正确

#### 麦克风问题
- 症状：日志显示 "AudioRecord 初始化失败"
- 解决：
  - 检查设备是否有麦克风
  - 检查其他应用是否能使用麦克风
  - 重启设备

### 4. 测试步骤
1. 安装应用：`adb install app/build/outputs/apk/debug/app-debug.apk`
2. 启用输入法：设置 → 语言和输入法 → 启用 SayitVoiceIME
3. 切换输入法：在任何输入框中，切换到 SayitVoiceIME
4. 查看日志：`adb logcat | grep VoiceKeyboard`
5. 点击录音按钮，观察日志输出

### 5. 期望的日志输出
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
```

### 6. 如果还是没反应
- 检查设备是否支持麦克风：`adb shell ls /dev/audio`
- 检查网络连接：`adb shell ping openspeech.bytedance.com`
- 重新安装应用
- 重启设备
- 尝试不同的设备

## 📞 需要帮助？
如果以上步骤都无法解决问题，请提供：
1. 完整的日志输出
2. 设备型号和Android版本
3. 具体的错误信息