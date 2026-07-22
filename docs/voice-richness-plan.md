# RepoLeaf 音色丰富与睡眠朗读方案

## 1. 目标与结论

当前小米系统 TTS 只向应用暴露一个中文离线女声，调节速度和音高只能改变节奏，不能改变声线。RepoLeaf 采用两层方案：

1. 系统 TTS 作为零下载、低成本兜底，并扫描设备上的全部中文离线 TTS 引擎与音色。
2. 以 sherpa-onnx + Kokoro v1.1 中文模型作为高质量本地音色方向，通过可选音色包提供睡眠、沉稳、清晰等不同声线。

基础 APK 不直接内置大模型。音色包必须由用户主动安装或下载，下载后可断网合成，正文不上传。

## 2. 调研依据

- Android `TextToSpeech` 支持枚举引擎和 `Voice`，并可设置音色、语速与音高；引擎提供多少音色由设备与引擎决定：<https://developer.android.com/reference/android/speech/tts/TextToSpeech>
- sherpa-onnx 支持 Android、Kotlin、ARM64 和完全离线 TTS，项目采用 Apache-2.0：<https://github.com/k2-fsa/sherpa-onnx>
- sherpa-onnx 提供官方 Android TTS Engine APK 和源码：<https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html>
- Kokoro v1.1 中文模型包含 103 个说话人，其中 55 个中文女声、45 个中文男声，采样率 24kHz：<https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/kokoro-multi-lang-v1_1.html>
- Kokoro v1.1 中文模型许可为 Apache-2.0，原始模型仓库约 394MB：<https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh>
- AISHELL-3 VITS 有 174 个中文说话人，但官方模型为 8kHz，适合作为轻量兼容备选，不作为睡眠场景首选：<https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html>

注意：sherpa-onnx 代码许可与具体模型许可必须分别核对。每次升级模型前保存来源、版本、SHA-256 与许可文本。

## 3. 产品方案

### 3.1 阅读工具栏

阅读页顶部只保留：返回、文章标题、听全文/暂停、收藏、设置。字号、主题、音色、语速和睡眠选项统一收进设置面板，避免小屏幕按钮重叠。

### 3.2 统一朗读设置

设置面板分为两组：

- 朗读：音色、语速、睡眠模式、睡眠定时、系统语音管理、Kokoro 音色包。
- 阅读：字号缩小/放大、深浅主题。

音色按 TTS 引擎分组，显示“引擎名称 · 音色名称 · 语言 · 离线”。选择后保存为默认音色；切换音色会使用独立缓存键。

### 3.3 睡眠模式

开启后默认使用 `0.88×` 语速与轻微低音调播放，正文仍可自由滚动，不自动跟随。支持关闭、15、30、45、60 分钟定时停止。后续版本增加结束前两分钟淡出。

睡眠模式不能把刺耳声线变成舒适声线，因此它只是节奏预设，最终体验依赖 Kokoro 精选音色。

### 3.4 Kokoro 音色包

原型阶段提供官方试听与 TTS Engine 安装入口。安装后 RepoLeaf 自动发现该引擎，不需要修改播放协议。

真机验证补充：官方 `TTS Engine: Next-gen Kaldi` 的 Android 标准 `TextToSpeech` 接口将 Kokoro 暴露为一个逻辑中文音色，而不是 103 个可由第三方应用逐一设置的 `Voice`。其官方配置页提供 `Speaker ID (0–102)`：3–57 是中文女声、58–102 是中文男声。RepoLeaf 在检测到该引擎时提供“配置 Kokoro 音色（Speaker ID）”深链接；用户在引擎页设置后，RepoLeaf 的后台朗读会使用这个音色。不能把同名 `zh` 语言变体伪装为多个独立音色。

质量结论：该外部 Kokoro 引擎在真机中文技术正文中会出现不自然发音和英文错误播报，故标注为“实验性试听”，并在音色排序中置后。稳定的系统中文离线语音仍是默认朗读引擎；后续只有在完成中文长文盲听验收后，才可将新的本地模型升级为默认。

### 3.5 MiMo 云端自然朗读

MiMo V2.5 TTS 作为用户主动启用的云端候选，而非替代离线默认。应用提供冰糖、茉莉、苏打和白桦四个中文预置音色，默认选择沉稳男声“苏打”。API Key 使用 Android Keystore 加密后保存在本机，不写入仓库、日志或 APK；更新设置时不会回显旧 Key。

播放服务按现有 Markdown 朗读分段调用 MiMo：只传输当前约 220 字以内的正文片段及朗读风格提示，不上传整个仓库或 GitHub Token。提示要求模型自然停顿、严格朗读原文且不补充原文没有的内容。用户可随时关闭 MiMo 回退到本地系统语音；无 Key 时不得静默调用云端。

正式集成阶段不直接展示 100 个编号，而是用同一篇中文长文盲听筛选 4～6 个明显不同的音色：

- 睡眠·温柔女声
- 睡眠·低沉男声
- 舒缓·成熟女声
- 沉稳·成熟男声
- 清晰·知识女声
- 清晰·知识男声

音色包页面显示大小、版本、许可、SHA-256、下载进度、是否仅 Wi-Fi 下载和删除入口。

## 4. 技术方案

### 4.1 当前版本

- `LocalTtsCatalog` 通过 `android.intent.action.TTS_SERVICE` 枚举所有安装的 TTS 引擎。
- 音色 ID 使用 `enginePackage:voiceName`，避免不同引擎同名冲突。
- `SpeechPreferencesStore` 保存默认音色、语速、睡眠模式和定时选择。
- `SpeechPlaybackService` 根据所选引擎初始化 `TextToSpeech`，使用 Media3 后台播放。
- 首段优先策略只等待一个 220 字以内分段；下一段在首段播放时预取。单段合成 45 秒仍未完成则可失败重试，准备中再次点朗读会取消任务。
- `SpeechVoicePackCatalog` 描述 Kokoro 包的来源、安装状态与试听入口。
- 对通过标准 API 暴露重复语言变体的引擎，按引擎包名和原始音色名去重；Kokoro 显示为“当前音色”，通过官方 `CONFIGURE_ENGINE` 页面设置 Speaker ID。
- `MimoTtsProvider` 按 MiMo OpenAI 兼容 Chat Completions 协议发送 TTS 请求：朗读正文位于 `assistant` 消息，风格约束位于 `user` 消息，返回的 Base64 WAV 继续交由同一 Media3 后台播放器播放。
- `MimoTtsCredentialStore` 复用 `CredentialStore` 的 AES-GCM Android Keystore 保护 API Key；云端启用状态和选中音色仅保存为普通偏好，不能反推密钥。

### 4.2 后续内置模型包

- 使用 sherpa-onnx AAR，在应用私有目录加载模型，不授予其他应用读取权限。
- 模型从固定 HTTPS 地址下载到临时文件，校验 SHA-256 后原子启用。
- 合成接口继续实现现有 `SpeechProvider`，系统 TTS 与 Kokoro 共用分段、缓存和播放器。
- 模型初始化、合成均在后台线程；首段优先，至少预取下一段。
- 基础 APK 不包含模型；卸载音色包只删除模型与其音频缓存，不影响仓库和阅读进度。

## 5. 测试与验收

### 5.1 功能

- 工具栏在 320dp 宽度下无重叠、无竖排按钮，文章标题可省略显示。
- 所有朗读相关设置只在统一面板出现。
- 默认音色、语速、睡眠模式重启后保留。
- 15/30/45/60 分钟定时到期停止服务并移除通知。
- 安装新 TTS Engine 后重新打开设置即可发现；切换后确实由所选引擎合成。
- 断网后已安装音色可继续合成，正文不产生网络请求。

### 5.2 睡眠体验

- 用同一篇 30 分钟中文长文盲听候选音色。
- 至少覆盖男声、女声、中英混排、数字、金融缩写和长段落。
- 记录刺耳感、疲劳感、停顿自然度、错音率和中英文切换突兀程度。
- 睡眠候选不得出现突然增益、爆音、连续漏字或段间长时间静音。

### 5.3 性能与安全

- 首次播放前两段准备时间、持续合成是否追上播放、峰值内存、30 分钟耗电均需真机记录。
- 下载中断后可继续或安全重试；校验失败的模型不能启用。
- 不在日志中记录文章正文、Token、模型下载签名材料或完整私有路径。

## 6. 迭代顺序

1. 当前迭代：统一设置面板、多引擎选择、睡眠预设、定时停止、Kokoro 试听与安装入口。
2. 原型验收：在 ARM64 真机安装官方 Kokoro Engine，筛选候选 speaker ID，验证后台播放。
3. 正式音色包：应用内下载、校验、启用与删除模型，精选 4～6 个音色。
4. 体验优化：音量归一化、结束淡出、逐音色发音词典与睡眠场景耗电优化。
