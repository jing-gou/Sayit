# 语音输入法测试指南

## 🔍 录音1秒就结束的问题

录音1秒自动结束，通常是 **WebSocket 连接失败**导致的。

### 可能的原因和解决方案：

#### 1. 网络连接问题
**症状**：日志显示 "无法连接到服务器" 或 "网络地址无法解析"
**检查**：
```bash
# 测试网络连接
adb shell ping openspeech.bytedance.com

# 或测试连通性
adb shell curl -I https://openspeech.bytedance.com
```

#### 2. API 配置错误
**症状**：日志显示 "认证失败" 或 401 错误
**检查**：
- 确认 `local.properties` 中的 API Key 正确
- 确认 Resource ID 正确（应为 `volc.seedasr.sauc.duration`）
- 确认 WebSocket URL 正确（`wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async`）

#### 3. 防火墙或代理问题
**症状**：连接超时
**检查**：
- 尝试关闭防火墙测试
- 检查是否有网络代理设置
- 尝试在不同的网络环境测试（Wi-Fi / 移动数据）

#### 4. 权限问题
**症状**：没有录音权限
**解决**：设置 → 应用 → SayitVoiceIME → 权限 → 录音 → 允许

## 📱 查看详细日志

### 方法1: 使用 ADB 命令
```bash
# 查看所有日志
adb logcat -s VoiceKeyboard:V

# 或过滤多个标签
adb logcat -s VoiceKeyboard:V VolcanoAuth:V OkHttp:V
```

### 方法2: 在 Android Studio 中查看
1. 打开 Android Studio
2. 连接设备
3. 打开 Logcat
4. 搜索 "VoiceKeyboard"

### 期望的日志输出
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
D/VoiceKeyboard: 已发送音频数据: 10000 字节
```

## 🛠️ 快速修复方案

### 方案1: 简化测试（排除网络问题）
修改代码，先不连接服务器，只测试录音功能：

```kotlin
private fun connectToASR() {
    // 模拟连接成功，直接开始录音
    scope.launch {
        try {
            // 创建一个假的 WebSocket 对象用于测试
            // 实际使用时注释掉这段
            Toast.makeText(this@VoiceKeyboard, "测试模式：模拟连接成功", Toast.LENGTH_SHORT).show()
            startRecording(null) // 传 null，但需要修改 startRecording 处理
        } catch (e: Exception) {
            showError("测试失败: ${e.message}")
        }
    }
}
```

### 方案2: 使用 HTTP 调试工具测试 API
使用 Postman 或 curl 测试 API 是否可用：
```bash
curl -X POST "https://openspeech.bytedance.com/api/v3/sauc/bigmodel_async" \
  -H "X-Api-Key: 你的API_KEY" \
  -H "X-Api-Resource-Id: volc.seedasr.sauc.duration" \
  -H "X-Api-Request-Id: $(uuidgen)" \
  -H "X-Api-Sequence: -1"
```

### 方案3: 降级使用 Android 原生语音识别
如果火山引擎 API 一直有问题，可以改用 Android 自带的 SpeechRecognizer API。

## 📞 需要进一步帮助？

请提供以下信息：
1. **完整的日志输出**（使用 `adb logcat -s VoiceKeyboard:V`）
2. **设备型号和 Android 版本**
3. **网络环境**（Wi-Fi / 移动数据 / 公司网络）
4. **API 配置**（只提供 Key 的前 8 位，完整 Key 请保密）

## 🔄 测试步骤清单

- [ ] 检查设备是否联网
- [ ] 检查应用是否有录音权限
- [ ] 查看 Logcat 日志输出
- [ ] 确认 API 配置正确
- [ ] 尝试在不同网络环境测试
- [ ] 如果以上都不行，尝试降级方案