# Sayit 你说

基于 Android `InputMethodService` 的**语音输入法**：以系统悬浮窗悬浮球为交互核心，结合流式语音识别（ASR）与大模型（LLM），在任意应用的输入框中完成听写、翻译、问答与快捷编辑。

---
## Demo视频

### 哔哩哔哩


---
## 第三方库与依赖

### Maven 依赖（`app/build.gradle.kts`）

| 依赖 | 版本 | 用途 |
|------|------|------|
| [AndroidX Core KTX](https://developer.android.com/jetpack/androidx/releases/core) | 1.12.0 | Kotlin 扩展与基础 API |
| [AndroidX AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat) | 1.6.1 | 设置页 `AppCompatActivity` |
| [AndroidX Activity KTX](https://developer.android.com/jetpack/androidx/releases/activity) | 1.8.2 | Activity Result API（选择悬浮球图片等） |
| [Material Components](https://github.com/material-components/material-components-android) | 1.11.0 | Material 主题与控件 |
| [OkHttp](https://square.github.io/okhttp/) | 4.12.0 | HTTP / WebSocket 客户端 |
| [OkHttp SSE](https://square.github.io/okhttp/features/events/) | 4.12.0 | LLM 流式 SSE 响应 |
| [Gson](https://github.com/google/gson) | 2.10.1 | JSON 序列化 / 解析 |
| [Kotlin Coroutines Android](https://github.com/Kotlin/kotlinx.coroutines) | 1.7.3 | 录音、网络、UI 协程 |
| [AndroidX DynamicAnimation](https://developer.android.com/jetpack/androidx/releases/dynamicanimation) | 1.0.0 | 悬浮球弹性吸附动画 |

### 构建工具链

| 组件 | 版本 |
|------|------|
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.20 |
| `compileSdk` / `targetSdk` | 34 |
| `minSdk` | 26 |
| JVM | 17 |

### 云端服务（非 Maven，需在 `local.properties` 或设置页配置）

| 服务 | 说明 | 典型配置项 |
|------|------|------------|
| **火山引擎 / 字节跳动流式 ASR** | WebSocket 实时语音识别 | `asr.api_key`、`asr.resource_id`、`asr.ws_url` |
| **兼容 OpenAI Chat Completions 的 LLM API** | 流式问答、翻译（默认XiaomiMIMO） | `llm.api_key`、`llm.api_url`、`llm.model` |

示例 ASR 端点见 [`TEST_GUIDE.md`](TEST_GUIDE.md)（如 `wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async`）。

### Android 系统 API（无额外三方库）

- `InputMethodService` / `InputConnection`：输入法与目标应用通信  
- `AudioRecord`：16 kHz PCM 单声道采集  
- `SYSTEM_ALERT_WINDOW`：悬浮球、轮盘、符号面板、气泡等 overlay  
- `ClipboardManager`：剪贴板历史监听与粘贴  

---

## 主要功能与操作逻辑

### 架构概览

```
用户应用输入框
       ↑ commitText / performEditorAction
VoiceKeyboard (IME，占位高度可趋近 0)
       ↑ 手势 / 状态
FloatingBallView (系统悬浮窗)
       ├─ WebSocket → 火山 ASR（流式识别）
       ├─ SSE → LLM（问答 / 翻译）
       └─ RadialMenu / SymbolPanel / ClipboardPanel / GestureGuide
```

IME 本体几乎不占屏幕：通过 `onEvaluateFullscreenMode` 与动态 `Insets`，仅在有符号面板时抬高占位高度；主要 UI 在**悬浮球 overlay** 上完成。

### 悬浮球基础操作

| 操作 | 行为 |
|------|------|
| **单击** | 在球心展开四向**轮盘菜单**：切换输入法、打开设置、剪贴板历史、符号/标点/Emoji 面板 |
| **长按** | 开始录音并连接 ASR WebSocket；松手结束录音，将识别结果写入当前输入框 |
| **拖动** | 移动悬浮球位置；松手后弹性吸附屏幕边缘 |

### 录音过程中的方向手势（`GestureActionHandler` + `VoiceMode`）

在**长按录音**状态下滑动，松手时按模式处理：

| 手势 | 模式 | 松手后逻辑 |
|------|------|------------|
| **上滑** | 取消 | 丢弃本次语音与 composing 预览，不提交 |
| **左滑** | 翻译 | 先得到 ASR 文本，再调用 LLM **流式翻译** 写入输入框 |
| **下滑** | 问答 | 先得到 ASR 文本，再作为问题调用 LLM **流式回答** |
| **右滑** | 发送 | 先提交语音识别文字，再对宿主应用执行 **发送**（`performEditorAction`）；多行输入框则回退为换行 |
| （无滑动） | 普通输入 | 仅提交识别文本 |

识别与 LLM 结果可通过 **结果气泡**（`ResultBubbleView`）展示流式增量；设置页可配置 LLM 提示词、翻译目标语言、ASR 语言等。

### 非录音时的编辑手势

| 手势 | 行为 |
|------|------|
| **左滑** | 按滑动距离**逐字删除**光标前文字；若存在选区则一次删除选中内容 |
| **右滑**（删除过程中） | 按 **LIFO** 栈**恢复**刚删字符，支持连续撤销式还原 |

### 轮盘与面板

- **符号面板**：常用中文标点、英文符号、Emoji 分类浏览，点击插入当前输入框；展开时更新 IME 底部 inset。  
- **剪贴板历史**：后台监听系统剪贴板，保存近期条目（带过期清理），从面板选择粘贴。  
- **设置**：ASR / LLM 密钥与地址、悬浮球大小、是否始终显示 overlay、自定义球体图片、手势引导等。

### 语音识别管线（简要）

1. 长按 → 创建 `AudioRecord`，经 OkHttp **WebSocket** 按协议推送 PCM。  
2. 服务端返回 JSON / 二进制包 → 增量更新 composing 与 `resultBuffer`。  
3. 松手 → 发送结束包；录音协程在 **300 ms grace** 内继续发送尾部音频，再触发 `deliverSpeechResults()`。  
4. 按 `VoiceMode` 决定直接 `commitText`、或走 `LLMService` 流式 SSE 后提交。

更详细的联调与日志说明见 [`TEST_GUIDE.md`](TEST_GUIDE.md)。

---

## 应用场景

| 场景 | 使用方式 |
|------|----------|
| **微信 / QQ / 短信等聊天** | 长按说话，右滑松手：**语音转文字并发送**；减少点按键盘 |
| **邮件 / 文档 / 备注** | 长按听写长段文字；左滑翻译外文草稿；符号面板补标点与 Emoji |
| **搜索 / 命令框** | 语音输入查询词；下滑对识别结果向 AI **追问** |
| **多语言沟通** | 说中文后左滑翻译为设置中的目标语言（如英文）再插入 |
| **复制粘贴密集型** | 轮盘打开剪贴板历史，快速重用近期复制内容 |
| **单手 / 大屏** | 悬浮球可拖动到拇指区域；IME 占位极低，不挡内容区 |
| **无障碍 / 减少击键** | 以语音为主、手势为辅，降低全键盘切换频率 |

---

## 原创功能

以下为相对传统「全键盘 IME」或「仅语音按钮」方案的设计点（实现于本仓库，非模板自带）：

1. **悬浮球 + 超低 IME 占位**：输入 UI 与系统键盘解耦，主交互在 overlay，宿主仅保留极薄占位与符号面板 inset。  
2. **录音方向手势多模态**：同一长按流程内，用上/下/左/右滑切换**取消、问答、翻译、发送**，无需额外按钮或模式切换页。  
3. **左滑渐进删除 + 右滑 LIFO 恢复**：未录音时的「滑动标尺」删字与可逆恢复，适配单手快速改错。  
4. **球心锚点四向轮盘**：单击展开设置 / 剪贴板 / 符号 / 切换输入法，菜单与悬浮球位置联动。  
5. **流式 ASR + 流式 LLM 一体**：识别与翻译/问答均支持增量展示（气泡 UI），松手后统一交付到 `InputConnection`。  
6. **句末 grace 音频**：松手后额外约 300 ms 继续上传音频并发送结束包，降低截断尾音导致的漏字。  
7. **剪贴板历史面板**：自动采集系统剪贴板条目，overlay 内选择粘贴。  
8. **可配置悬浮球**：大小滑条、自定义图片、是否仅在输入态显示 overlay（`overlay_always`）。  
9. **内置手势引导页**（`GestureGuideView`）：首次或从设置唤起，说明全部球体与录音手势。  
10. **聊天场景发送语义**：右滑发送走 `performEditorAction`，避免误触 `KEYCODE_ENTER` 在单行框换行、多行框又能换行的分支逻辑。

---

## 项目结构（主要源码）

```
app/src/main/java/org/sayit/voiceime/
├── VoiceKeyboard.kt          # IME 核心：ASR WebSocket、overlay、insets、提交文本
├── SettingsActivity.kt       # 设置页
├── PermissionActivity.kt     # 权限引导
├── AppSettings.kt            # SharedPreferences / BuildConfig 配置
├── action/GestureActionHandler.kt
├── api/LLMService.kt
├── clipboard/                # 剪贴板历史
├── gesture/GestureAction.kt
├── overlay/                  # 结果气泡、窗口辅助
└── widget/                   # 悬浮球、轮盘、符号面板、手势引导
```

---

## 安装与启用

### 1. 配置密钥

复制或编辑项目根目录 `local.properties`（勿提交敏感信息）：

```properties
asr.api_key=你的ASR密钥
asr.resource_id=volc.seedasr.sauc.duration
asr.ws_url=wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async

llm.api_key=你的LLM密钥
llm.api_url=https://你的网关/v1/chat/completions
llm.model=doubao
```

也可安装后在应用内 **设置** 页面修改（写入 `SharedPreferences`）。

### 2. 编译安装

```bash
cd SayitVoiceIME
./gradlew installDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat installDebug
```

### 3. 系统内启用

1. **设置 → 语言和输入法 → 虚拟键盘 → 管理键盘** → 启用「Sayit 语音输入法」  
2. 在任意输入框切换输入法到本 IME  
3. 授予 **录音**、**悬浮窗（显示在其他应用上层）** 权限  

---

## 从一张 PNG 生成各规格启动图标

**方式 A — 脚本（推荐，一条命令）**

准备正方形 PNG（建议 ≥1024×1024，主体在画面中心）。在项目根目录执行：

```powershell
.\scripts\generate-launcher-icons.ps1 -Source "你的图标.png"
```

会写入 `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` 下的 `ic_launcher.png`（方图）与 `ic_launcher_round.png`（圆形裁剪）。然后重新编译安装即可。

| 目录 | 边长 (px) |
|------|-----------|
| mipmap-mdpi | 48 |
| mipmap-hdpi | 72 |
| mipmap-xhdpi | 96 |
| mipmap-xxhdpi | 144 |
| mipmap-xxxhdpi | 192 |

**方式 B — Android Studio**

`File` → `New` → `Image Asset` → **Launcher Icons (Adaptive and Legacy)** → 选你的 PNG → Next → Finish。会自动生成 mipmaps 与 adaptive 资源。

当前 `AndroidManifest.xml` 使用 `@mipmap/ic_launcher` 与 `@mipmap/ic_launcher_round`，与上述输出一致。

---



