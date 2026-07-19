# Android 知识库阅读器调研

调研日期：2026-07-19。数据来自 GitHub 公开 API 和各项目仓库主页，Star 数会持续变化。

## 结论

没有发现一个成熟开源项目同时满足：Git/GitHub 类平台同步、Android 原生友好体验，以及 Markdown、HTML、PDF、XLSX 的统一只读阅读。最接近的方案是组合 GitJournal 的仓库同步思路、Markor 的 Markdown 体验、KOReader/Librera 的文档阅读能力。

| 项目 | 规模（约） | 强项 | 与本需求的差距 |
|---|---:|---|---|
| [Markor](https://github.com/gsantner/markor) | 5.8k stars | Android、Markdown/文本、HTML 预览、离线 | 同步依赖外部工具；PDF 以导出为主；无 XLSX 阅读 |
| [GitJournal](https://github.com/GitJournal/GitJournal) | 4.2k stars | GitHub/GitLab/自托管 Git 同步、Markdown、移动优先 | 聚焦笔记；PDF/HTML/XLSX 不是核心格式 |
| [KOReader](https://github.com/koreader/koreader) | 27.8k stars | Android、多格式电子书、PDF 阅读能力成熟 | UI 和模型偏电子书；无 Git 知识库/XLSX |
| [Librera Reader](https://github.com/foobnix/LibreraReader) | 4.6k stars | Android 本地多格式阅读 | 无 Git 同步；知识库导航弱；XLSX 非核心 |
| [Logseq](https://github.com/logseq/logseq) | 43.9k stars | Markdown 知识图谱、移动端、链接体系 | 更像编辑型 PKM；PDF/Office 是附件体验；整体较重 |
| [IReader](https://github.com/IReaderorg/IReader) | 0.9k stars | Kotlin、多端、离线阅读、现代 UI | 聚焦网络小说/电子书源，不是文档知识库 |
| [PDF Reader Pro](https://github.com/ahmmedrejowan/PdfReaderPro) | 0.1k stars | Kotlin、Material 3、PDF 搜索/书签 | 仅能作为 PDF 交互参考 |

## 可复用的设计信号

- GitJournal：仓库是数据源，本地副本是运行时数据；同步失败不应阻塞阅读。
- Markor：Markdown 转 HTML 后用 WebView 呈现，主题、目录和内部链接是体验关键。
- KOReader/Librera：阅读进度、最近阅读、收藏、夜间模式、缩放和大文档性能比格式数量更重要。
- Android 官方 Now in Android：Compose、单向数据流、离线优先、分层模块可作为工程结构参考。

## 建议的 MVP

1. 配置一个 GitHub/GitLab/Gitea 仓库与分支，只做拉取，不在手机端提交。
2. 首次下载仓库快照，之后按 ETag/commit SHA 增量判断；所有资料落本地后阅读。
3. 文件树、最近阅读、收藏、标题/路径搜索、同步状态和错误恢复。
4. Markdown/HTML 支持目录、内部相对链接、代码高亮、图片与深浅主题。
5. PDF 支持翻页、缩放、文本搜索和进度记忆。
6. XLSX 首版只读：工作表切换、冻结首行、横向滚动；不承诺公式重算和复杂图表。

## 不建议首版实现

- 完整 Git 写入、合并和冲突解决。
- 手机端 Office 编辑器。
- 服务端全文索引或多人协作。
- 为兼容全部 Excel 特性引入完整 LibreOffice 内核。

