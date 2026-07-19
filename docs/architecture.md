# 技术方案

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- 构建基线：JDK 17、AGP 8.13.2、Gradle 8.13、compileSdk 36；依赖升级需保留 API 36 兼容检查
- Clean-ish 分层：`ui` / `domain` / `data` / `renderer`
- Room 保存资料索引、收藏与阅读进度；文件正文保存在应用私有目录
- WorkManager 执行后台同步；OkHttp/Retrofit 对接平台 API
- Markdown：CommonMark/flexmark 转安全 HTML，再由 WebView 展示
- HTML：WebView + 本地资源拦截，默认禁用不必要的脚本和跨域访问
- PDF：AndroidX PDF Viewer；当前仍为 alpha，生产前保留基于 `PdfRenderer` 的降级方案
- XLSX：直接解析 OOXML 的 workbook/sharedStrings/worksheet，流式生成虚拟化表格；复杂表格可由 CI 预转换为 HTML/PDF

## 数据流

```text
GitHub / GitLab / Gitea
          |
          v
RepositoryProvider -> SnapshotSync -> Local files
                             |             |
                             v             v
                       DocumentIndex -> RendererRegistry
                                             |
                         +---------+---------+--------+
                         |         |         |        |
                    Markdown     HTML       PDF      XLSX
```

## 安全边界

- Token 存 Android Keystore 加密存储，不写仓库、不写日志。
- 私有资料只保存在应用私有目录；导出必须由用户主动触发。
- HTML/Markdown 默认不加载远程脚本，外链交给系统浏览器确认打开。
- ZIP 快照解压必须防 Zip Slip，并限制总大小、文件数和单文件大小。

## 迭代拆分

- M0（当前）：可构建 Compose 工程、首页交互稿、调研和环境脚本。
- M1：公开仓库快照同步、文件树、Markdown/HTML 阅读、阅读进度。
- M2：私有仓库 Token、PDF、XLSX、搜索、收藏、后台同步。
- M3：增量同步、GitLab/Gitea provider、平板自适应、性能与无障碍。
