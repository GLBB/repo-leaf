# RepoLeaf MVP 技术方案

版本：`0.1`  
状态：Draft  
对应产品方案：[MVP 产品方案](mvp-product.md)

## 1. 技术目标

- 多仓库之间配置、文件、索引、同步任务和错误状态完全隔离。
- 网络只是同步依赖，不是阅读依赖；所有阅读操作以本地数据为准。
- MVP 用独立 Markdown 渲染器把阅读体验做深；统一渲染接口为后续 HTML、PDF、XLSX 留出扩展点。
- 同步过程具备事务语义：新版本完整成功后才能替换旧版本。
- MVP 保持单应用模块和清晰包边界，避免过早多模块化。

## 2. 构建基线

| 项目 | 版本 |
|---|---|
| Kotlin | 2.2.21 |
| Jetpack Compose | BOM 2026.06.00 |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| JDK | 17 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 28 |

Lifecycle 固定在 `2.10.0`，因为 `2.11.0` 要求 AGP 9.1 和 compileSdk 37。升级依赖时必须执行 AAR Metadata 和完整构建检查。

## 3. 总体架构

采用单向数据流和分层架构：

```text
Compose UI
    |
ViewModel / UI State
    |
Use Cases
    |
+---------------- Domain Interfaces ----------------+
| RepositoryProvider | SyncEngine | DocumentRenderer |
+----------------------------------------------------+
    |                       |                 |
GitHub REST API          Room + Files      Format adapters
```

建议包结构：

```text
io.github.glbb.repoleaf
├── app                 # Application、导航、依赖装配
├── feature
│   ├── home
│   ├── repositories
│   ├── documents
│   ├── reader
│   ├── search
│   └── settings
├── domain
│   ├── model
│   ├── repository
│   └── usecase
├── data
│   ├── db
│   ├── github
│   ├── sync
│   ├── storage
│   └── preferences
└── renderer
    ├── markdown
    ├── html
    ├── pdf
    └── xlsx
```

MVP 保留一个 Gradle `app` 模块；当渲染器编译时间、依赖冲突或团队协作出现明确成本时再拆分模块。

## 4. 核心数据模型

### 4.1 Room 实体

#### RepositoryEntity

| 字段 | 类型 | 说明 |
|---|---|---|
| id | UUID String | 本地主键 |
| accountId | String? | 私有仓库关联的 GitHub 账号；公共匿名仓库可为空 |
| provider | String | MVP 固定为 `github` |
| owner | String | 仓库所有者 |
| repo | String | 仓库名称 |
| displayName | String | 用户显示名称 |
| branch | String | 目标分支 |
| rootPath | String | 可选资料子目录 |
| enabled | Boolean | 是否参与聚合与同步 |
| remoteCommit | String? | 最近成功同步 SHA |
| lastSyncedAt | Instant? | 最近成功时间 |
| syncState | Enum | 同步状态 |
| localBytes | Long | 本地占用 |

唯一约束建议使用 `(provider, owner, repo, branch, rootPath)`，同时允许用户复制配置后修改其中任一项。

#### GitHubAccountEntity

保存 GitHub user ID、login、头像 URL、连接状态和凭证别名。实体不保存访问令牌或刷新令牌明文；令牌正文由 Keystore 保护的凭证存储管理。

#### DocumentEntity

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String | `SHA-256(repositoryId + relativePath)` |
| repositoryId | String | 外键 |
| relativePath | String | 仓库内规范化路径 |
| title | String | 展示标题 |
| type | Enum | MARKDOWN / HTML / PDF / XLSX / OTHER；MVP 仅内置渲染 MARKDOWN |
| mimeType | String | MIME |
| size | Long | 字节数 |
| modifiedCommit | String | 所属快照 SHA |
| searchableText | String? | 标题/路径等轻量搜索字段 |

索引：`repositoryId`、`title`、`relativePath`、`type`。MVP 使用 Room FTS 保存标题、路径和可选的 Markdown 纯文本摘要；不索引其他格式正文。

#### ReadingStateEntity

| 字段 | 说明 |
|---|---|
| documentId | 文档主键 |
| progressKind | SCROLL / PAGE / SHEET_CELL |
| progressValue | JSON 或格式化字符串 |
| isFavorite | 是否收藏 |
| lastOpenedAt | 最近打开时间 |

文档在新快照中仍存在时保留阅读状态；被删除的文档状态保留 30 天后清理。

#### SyncRunEntity

记录仓库、开始/结束时间、目标 SHA、阶段、结果、错误类型和统计信息。不得保存 Token、完整响应头或可能含凭证的 URL。

## 5. 本地存储布局

所有正文位于应用私有目录：

```text
files/repositories/<repositoryId>/
├── current -> snapshots/<commitSha>/
├── snapshots/
│   └── <commitSha>/
│       └── <rootPath contents>
└── staging/
    └── <syncRunId>/

cache/renderers/
├── markdown/
├── html/
├── pdf/
└── xlsx/
```

Android 不提供通用可靠的目录符号链接能力时，`current` 使用数据库中的 active commit 指针代替。切换 active commit 与索引更新在同一 Room 事务中完成。

存储约束初值：

- 单仓库快照默认上限 500 MB。
- 单文件默认上限 100 MB；内置 Markdown 渲染上限初值为 5 MB，超过时提供纯文本或系统应用降级入口。
- ZIP 文件数上限 50,000。
- 解压后总大小不得超过压缩包大小的 20 倍或配置上限。
- 仅保留当前快照和上一个成功快照；同步稳定后清理更旧版本。

## 6. GitHub Provider

### 6.1 接口

```kotlin
interface RepositoryProvider {
    suspend fun resolveRepository(config: RepositoryConfig): RemoteRepository
    suspend fun resolveCommit(config: RepositoryConfig): RemoteCommit
    suspend fun downloadSnapshot(
        config: RepositoryConfig,
        commit: RemoteCommit,
        destination: Path,
        progress: (DownloadProgress) -> Unit,
    )
}
```

GitHub MVP 使用 REST API：

- 仓库信息：`GET /repos/{owner}/{repo}`
- 分支/commit：`GET /repos/{owner}/{repo}/commits/{ref}`
- 快照：`GET /repos/{owner}/{repo}/zipball/{ref}`

请求必须设置明确的 API 版本、`User-Agent`、超时和取消机制。响应处理 `ETag`、速率限制、404、401、403、重定向和服务端错误。

### 6.2 GitHub App Device Flow

- Release 版使用 RepoLeaf GitHub App 的 Device Flow，不在 APK 中存放 client secret。
- GitHub App 权限仅配置 `Repository contents: Read-only` 和 GitHub 必需的 metadata 读取。
- 用户必须安装 GitHub App，并明确选择允许 RepoLeaf 访问的仓库。
- Device Flow 严格遵守服务端返回的轮询 `interval`，处理 `authorization_pending`、`slow_down`、`expired_token`、`access_denied` 和取消。
- 获取令牌后立即调用 `/user` 重新验证账号身份，避免账号切换造成数据混用。
- MVP 支持一个 GitHub 账号，多账号放到后续版本。

### 6.3 凭证生命周期

- User access token 和可选 refresh token 使用 Android Keystore 保护的加密存储。
- 启用 GitHub App expiring user access tokens 时，access token 默认短期有效；应用在到期前按官方流程刷新。
- 内存中只在构造请求时读取凭证，禁止写入 Room、日志、Crash 报告或同步错误详情。
- 401 时只允许一次受控刷新和重试；失败后标记账号需要重新连接，保留离线数据。
- 断开账号时清除访问和刷新凭证，并取消依赖该账号的网络任务。
- 开发构建可以支持 fine-grained PAT 调试入口，但不得作为 Release 默认流程；PAT 只允许目标仓库的 `Contents: read`。

### 6.4 私有仓库发现与下载

- 使用 GitHub App user access token 查询用户可访问的 installations 和安装内仓库，只展示权限交集内的仓库。
- 私有仓库 archive endpoint 要求 `Contents: read` 权限。
- 私有仓库 ZIP 重定向 URL 为短期地址，不持久化、不记录日志，获得后立即下载。
- 下载重定向时不得把 Authorization Header 发送到非 GitHub 认可的任意主机；网络层维护允许的 GitHub 下载域名策略。

## 7. 同步引擎

### 7.1 状态机

```text
IDLE
  -> CHECKING_REMOTE
  -> DOWNLOADING
  -> EXTRACTING
  -> INDEXING
  -> ACTIVATING
  -> SUCCESS

任一阶段 -> FAILED / CANCELLED -> IDLE
```

### 7.2 算法

1. 获取仓库级互斥锁，防止同一仓库并发同步。
2. 检查网络、可用空间和仓库配置。
3. 请求目标分支 commit SHA。
4. SHA 与当前版本相同则结束。
5. 下载到 `staging/<runId>/snapshot.zip.part`。
6. 校验 HTTP 长度和 ZIP 基本完整性。
7. 安全解压，拒绝绝对路径、`..`、符号链接和超限内容。
8. 过滤 rootPath，扫描受支持文件并生成新索引。
9. Room 事务内写入索引并切换 active commit。
10. 删除下载临时文件，异步清理旧快照。

任何失败都不得修改 active commit。应用进程被杀后，下次启动清理过期 staging 目录并将未结束任务标记为中断。

### 7.3 调度

- MVP 默认仅手动同步。
- “同步全部”使用受限并发，建议最多同时下载 2 个仓库。
- WorkManager 用于保证切后台后任务可继续；大文件使用前台通知。
- 网络约束、计费网络策略和自动同步频率在后续版本启用。

## 8. 文档渲染

### 8.1 统一接口

```kotlin
interface DocumentRenderer {
    fun supports(type: DocumentType): Boolean
    @Composable
    fun Render(
        document: LocalDocument,
        initialState: ReadingState?,
        onStateChanged: (ReadingState) -> Unit,
        onLink: (DocumentLink) -> Unit,
    )
}
```

Renderer 不直接导航或访问远端仓库；相对链接解析由统一 `DocumentLinkResolver` 完成。

### 8.2 Markdown

- 使用 CommonMark 或 flexmark 解析为 HTML。
- 支持 GFM 表格、任务列表、代码围栏和自动链接扩展。
- 生成语义目录和稳定 heading anchor。
- 图片与相对链接转换为受控的本地内容 URL。
- 原始 HTML 默认清洗；脚本、iframe、事件属性和危险 scheme 被移除。

### 8.3 Markdown MVP 质量要求

- CommonMark 基础语法和 GFM 常用扩展结果必须有快照测试。
- YAML front matter 只提取元数据，不直接作为正文显示。
- 目录跟随滚动高亮，点击目录后定位到稳定 heading anchor。
- 相对图片支持 URL 编码、中文路径、空格、`./` 和 `../`，但不得越过仓库根目录。
- 相对 Markdown 链接支持文件跳转、锚点跳转和返回原阅读位置。
- 代码块横向滚动且不撑破正文宽度；可复制，但 MVP 不要求完整 IDE 级高亮。
- 正文宽度、行高、字体缩放、深色模式和系统动态字体必须在手机尺寸上可读。
- 解析与 HTML 生成放在后台线程，WebView/Compose 更新只在主线程执行。

### 8.4 Post-MVP：HTML

- 使用 WebView 展示本地内容。
- 禁止文件系统任意访问、通用 `file://` 跨域和不必要的 JavaScript。
- 通过 `WebViewAssetLoader` 或自定义请求拦截器读取当前仓库资源。
- `http/https` 外链不在 WebView 内静默打开，由 UI 提示后交给系统浏览器。

### 8.5 Post-MVP：PDF

- 首选 AndroidX PDF Viewer；其版本仍处于 alpha，因此通过适配器隔离。
- 保留基于 `PdfRenderer` 的只读降级实现，用于不兼容设备或库回归。
- 进度使用页码和页内相对偏移；文件更新后页数变化时做边界修正。
- 大文件避免整份加载到内存，渲染页位图必须按生命周期释放。

### 8.6 Post-MVP：XLSX

- MVP 只读，不引入完整 Office 引擎。
- 使用 ZIP + XML 流式解析 OOXML：workbook、relationships、sharedStrings、styles、worksheet。
- 单元格模型按可视区域分页/虚拟化，不构造整表 Compose 节点。
- 公式优先显示文件中缓存结果，不执行公式计算。
- 不支持宏、图表、数据透视表、外部连接；明确显示降级提示。
- 防止 ZIP Bomb、超大 sharedStrings 和 XML 实体扩展攻击。

## 9. 导航与链接

内部链接解析顺序：

1. 当前文档相对路径。
2. 去除 URL fragment 后查找目标文件。
3. 目录链接尝试 `README.md` 或 `index.html`。
4. fragment 交给目标渲染器定位。
5. 目标不存在时显示断链提示，不跳出应用。

不允许相对路径越过当前仓库根目录。跨仓库链接 MVP 不自动解析。

## 10. 错误模型

统一错误类型：

```text
NetworkUnavailable
AuthenticationRequired
AuthorizationPending
AuthorizationExpired
PermissionDenied
RepositoryNotFound
RateLimited(resetAt)
InsufficientStorage(requiredBytes)
SnapshotTooLarge
UnsafeArchive
CorruptDocument
UnsupportedDocumentFeature
Unknown(correlationId)
```

Domain 错误不得直接携带 HTTP body、访问令牌或本地绝对路径。UI 将错误映射为简短说明和“重试、重新连接 GitHub、释放空间”等操作。

## 11. 安全与隐私

- 使用 GitHub App 最小权限，只申请 Contents read-only，不申请仓库写权限。
- 网络仅允许 HTTPS，发布构建禁用明文流量。
- 解压路径必须经过规范化并验证仍位于 staging 根目录。
- HTML/Markdown 内容按不可信输入处理。
- 日志对仓库私有路径、账号和 URL 参数做脱敏。
- 应用备份策略不得将访问/刷新令牌和私有仓库正文上传到系统云备份。
- Release 构建开启 R8，并保留依赖所需最小规则。

## 12. 可观测性

MVP 本地记录：

- 同步阶段耗时、下载字节数、文档数量和结果。
- 渲染器打开耗时、文件类型和大小区间。
- 非敏感错误类型和 correlation ID。

默认不接入第三方遥测。未来如引入崩溃分析，必须提供隐私说明并确保不上传文档内容、路径和访问令牌。

## 13. 测试与发布

- Domain、路径规范化、同步状态机、Markdown 解析和链接解析使用 JVM 单元测试。
- Room、WorkManager、Markdown WebView/Compose 容器使用设备或模拟器集成测试。
- Compose 核心流程使用 UI 自动化测试。
- 每次合并执行 `assembleDebug`、`testDebugUnitTest`、`lintDebug`。
- 发布候选执行测试方案规定的设备矩阵、离线、升级和安全回归。

详细门禁见 [MVP 测试方案](mvp-test-plan.md)。

## 14. 实施顺序

1. 建立 Room schema、RepositoryProvider 和依赖装配。
2. 完成 GitHub App Device Flow、公共/私有仓库配置、下载、解压、索引和文件树。
3. 接入 Markdown、目录、图片、代码块、内部链接和阅读主题。
4. 完成收藏、最近阅读、进度和聚合搜索。
5. 完成 Keystore 凭证、私有仓库选择、令牌刷新与撤销。
6. 完成 Markdown 大文件保护、私有仓库安全、兼容性、性能、无障碍和发布回归。
7. MVP 发布后再接入 HTML、PDF 和 XLSX 内置渲染器。

## 15. GitHub 官方参考

- [GitHub App user access token 与 Device Flow](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app)
- [GitHub App 与 OAuth App 差异](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/differences-between-github-apps-and-oauth-apps)
- [Repository archive API 与 Contents read 权限](https://docs.github.com/en/rest/repos/contents)
- [GitHub App 安全最佳实践](https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/best-practices-for-creating-a-github-app)
