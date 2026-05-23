# SayitVoiceIME - 最小语音输入法骨架

## 项目结构

```
SayitVoiceIME/
├── app/
│   ├── build.gradle.kts          # 应用模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml   # IME 声明 + 录音权限
│       ├── java/org/sayit/voiceime/
│       │   └── VoiceKeyboard.kt  # 核心类 (继承 InputMethodService)
│       └── res/
│           ├── xml/method.xml    # IME 配置
│           └── values/strings.xml
├── settings.gradle.kts           # 项目配置
├── build.gradle.kts              # 根构建配置
└── gradle.properties
```

## 核心文件说明

### VoiceKeyboard.kt (约 50 行)
```kotlin
onCreateInputView()  → 返回一个麦克风按钮
toggleListening()    → 切换录音状态
startSpeechRecognition()  → TODO: 实现语音识别
commitText(text)     → 提交识别结果到输入框
```

## 下一步：集成语音识别

选择方案后告诉我，我来帮你集成：

| 方案 | 优点 | 缺点 |
|------|------|------|
| **SpeechRecognizer** (系统 API) | 免费、无网络 | 仅支持 Google 设备、不流式 |
| **OpenAI Realtime API** | 真流式、高精度 | 需付费、需要网络 |
| **Whisper.cpp** | 本地、隐私好 | 模型大、流式复杂 |

## 安装到设备

```bash
cd SayitVoiceIME
./gradlew installDebug
```

然后在手机设置中启用：
设置 → 语言和输入 → 虚拟键盘 → 管理键盘 → 启用 "Sayit 语音输入法"