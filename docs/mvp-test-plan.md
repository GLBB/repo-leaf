# RepoLeaf MVP 测试方案

版本：`0.1`  
状态：Draft  
测试重点：多仓库离线同步与高质量 Markdown 阅读

## 1. 测试目标

验证 RepoLeaf MVP 能在支持的 Android 设备上稳定完成：

1. 配置多个 GitHub 仓库。
2. 安全、可恢复地同步仓库快照。
3. 在在线和离线状态下浏览资料。
4. 正确、流畅地阅读 Markdown 文档。
5. 保存收藏、最近阅读和阅读进度。
6. 在网络、鉴权、存储和内容异常时保留上一次可用数据。

HTML、PDF、XLSX 在 MVP 只验证文件识别、元数据展示和系统应用打开，不验证内置渲染质量。

## 2. 质量风险排序

| 优先级 | 风险 | 影响 |
|---|---|---|
| P0 | 同步失败破坏本地旧版本 | 离线资料丢失或不可读 |
| P0 | Token 泄漏到日志或数据库 | 私有仓库泄露 |
| P0 | ZIP Slip / ZIP Bomb | 文件覆盖、磁盘耗尽或安全事故 |
| P0 | Markdown 相对链接、图片大量失效 | 核心阅读体验不可用 |
| P1 | 多仓库文档 ID 冲突 | 收藏和进度串库 |
| P1 | 大 Markdown 导致卡死或 OOM | 应用稳定性差 |
| P1 | 深色模式、字体缩放不可读 | 日常阅读体验差 |
| P2 | 其他文件无法调用系统应用 | 非核心格式缺少降级路径 |

## 3. 测试范围

### 3.1 MVP 范围内

- GitHub 公共仓库，以及通过 RepoLeaf GitHub App Device Flow 授权的私有仓库。
- 最多 10 个仓库的添加、编辑、停用、同步和删除。
- commit SHA 检查、快照下载、安全解压、索引、原子切换和失败恢复。
- 文件树、全部资料、最近阅读、收藏、标题/路径搜索。
- Markdown 解析、布局、目录、链接、图片、代码、主题和阅读进度。
- HTML、PDF、XLSX 的识别、错误提示和外部打开。
- Android 9 及以上兼容性、性能、无障碍和基础安全。

### 3.2 MVP 范围外

- Git 写入、合并、冲突处理、Git LFS、Submodule。
- GitLab、Gitea 和 SSH。
- HTML、PDF、XLSX 内置渲染质量。
- iOS、桌面端、平板专用布局。

## 4. 测试分层

| 层级 | 目标 | 建议工具 | CI 频率 |
|---|---|---|---|
| JVM 单元测试 | Domain、路径、状态机、Markdown AST/HTML、链接解析 | JUnit、Truth/Kotest | 每次提交 |
| 数据层测试 | Room migration/DAO、索引事务、状态保留 | Robolectric 或设备测试 | 每次 PR |
| 网络契约测试 | GitHub 响应、重定向、限流、下载中断 | MockWebServer | 每次 PR |
| 设备集成测试 | WorkManager、文件、Keystore、WebView | AndroidX Test | 每次 PR/夜间 |
| Compose UI 测试 | 添加仓库、浏览、搜索、收藏、错误恢复 | Compose UI Test | 每次 PR |
| 手工探索测试 | 阅读体验、复杂 Markdown、系统外部打开 | 真机 | 发布候选 |
| 性能测试 | 启动、同步、索引、打开、滚动、内存 | Macrobenchmark、Profiler | 夜间/发布候选 |

## 5. 测试环境矩阵

### 5.1 Android 版本

| 级别 | 环境 | 用途 |
|---|---|---|
| 必测 | Android 9 / API 28 模拟器 | 最低版本兼容 |
| 必测 | Android 12 / API 31 真机或模拟器 | 中间版本、存储与 WebView |
| 必测 | Android 15 / API 35 真机 | 主流新系统 |
| 必测 | Android 16 / API 36 模拟器 | targetSdk/compileSdk 基线 |
| 选测 | 小屏 360×640、低内存设备 | 布局与内存压力 |
| 选测 | 大屏手机、横屏 | 自适应和重建 |

至少包含一台国内厂商 ROM 真机，检查后台任务、深色模式、字体缩放和系统文件打开行为。

### 5.2 网络状态

- 正常 Wi-Fi。
- 高延迟 500 ms。
- 限速 1 Mbps。
- 下载中途断网。
- DNS 失败。
- 飞行模式。
- HTTP 401、403、404、429、500、502。
- GitHub 重定向与下载 URL 过期。

### 5.3 仓库规模

| 数据集 | 文档数 | 快照大小 | 目的 |
|---|---:|---:|---|
| Tiny | 20 | < 1 MB | 冒烟和 UI 自动化 |
| Normal | 1,000 | 50 MB | 日常功能和性能 |
| Large | 10,000 | 500 MB | 索引、搜索和存储上限 |
| Overflow | > 50,000 | > 500 MB | 限制和错误恢复 |

## 6. Markdown 基线测试库

在独立 fixture 仓库维护以下文档，所有测试资源不包含真实私有资料：

```text
markdown-fixtures/
├── commonmark/
├── gfm/
├── front-matter/
├── headings-and-toc/
├── code-blocks/
├── tables/
├── images/
│   ├── relative/
│   ├── spaces-and-chinese-paths/
│   ├── svg/
│   └── missing/
├── links/
│   ├── same-file-anchor.md
│   ├── cross-file.md
│   ├── encoded-path.md
│   └── broken-link.md
├── security/
├── accessibility/
└── performance/
```

必须覆盖：

- CommonMark 标题、段落、强调、列表、引用、分隔线和转义。
- GFM 表格、任务列表、删除线、自动链接和 fenced code。
- YAML front matter，有/无 `title`，格式损坏和超大字段。
- 重复标题、中文标题、emoji、特殊符号和稳定 anchor。
- 行内代码、超长代码行、多语言代码块、复制操作。
- 相对图片、上级目录、URL 编码、空格、中文文件名、SVG 和损坏图片。
- 当前文件锚点、跨文件锚点、目录链接、外链、断链和越界路径。
- 1 MB、5 MB、超过限制的 Markdown；10,000 行和超长单行。
- 恶意 HTML、`javascript:` URL、iframe、事件属性和远程脚本。

## 7. 核心功能用例

### 7.1 多仓库

| ID | 场景 | 预期结果 |
|---|---|---|
| REPO-001 | 添加公共仓库 | 自动识别 owner/repo/default branch，同步成功 |
| REPO-002 | 添加三个不同仓库 | 首页均可见，目录和状态隔离 |
| REPO-003 | 同仓库不同分支 | 两个知识库可以共存，文档 ID 不冲突 |
| REPO-004 | 同仓库不同 rootPath | 只索引指定子目录 |
| REPO-005 | 无效 URL | 阻止保存并给出可操作提示 |
| REPO-006 | 停用仓库 | 不参与同步全部和聚合搜索，离线数据保留 |
| REPO-007 | 删除仓库 | 确认后删除对应文件和索引，不影响其他仓库 |
| REPO-008 | 达到 10 个仓库 | 禁止继续添加并说明 MVP 上限 |

### 7.2 GitHub App 与私有仓库

| ID | 场景 | 预期结果 |
|---|---|---|
| AUTH-001 | GitHub App Device Flow 授权 | 登录成功并验证正确 GitHub 账号 |
| AUTH-002 | 只选择部分私有仓库 | 只展示和同步已授权仓库 |
| AUTH-003 | 未安装 GitHub App | 引导安装并选择仓库，不陷入登录循环 |
| AUTH-004 | Device code 过期/拒绝 | 显示重新授权，不保存半完成凭证 |
| AUTH-005 | 组织 SSO 未激活 | 给出可操作提示，其他仓库不受影响 |
| AUTH-006 | Access token 到期 | 受控刷新后继续；刷新失败则要求重新连接 |
| AUTH-007 | 用户撤销 GitHub App | 已同步资料仍可离线阅读，下一次同步提示重新连接 |
| AUTH-008 | 重启应用 | 凭证可继续使用但不在 UI 明文回显 |
| AUTH-009 | 检查日志、数据库、备份 | 不含 access token、refresh token、Authorization Header 或临时下载 URL |
| AUTH-010 | GitHub App 权限审计 | 仅 Contents read-only，无仓库写权限 |

### 7.3 同步一致性

| ID | 场景 | 预期结果 |
|---|---|---|
| SYNC-001 | SHA 未变化 | 不重复下载，状态为已是最新 |
| SYNC-002 | SHA 变化 | 新快照和索引同时生效 |
| SYNC-003 | 下载 50% 断网 | 任务失败，旧快照继续可读，临时文件可清理 |
| SYNC-004 | 解压时进程被杀 | 重启后仍使用旧快照，并清理过期 staging |
| SYNC-005 | 索引事务失败 | active commit 不改变 |
| SYNC-006 | 两个仓库并发同步 | 互不覆盖，进度分别显示 |
| SYNC-007 | 同仓库重复触发 | 合并或拒绝重复任务，不并发写同一目录 |
| SYNC-008 | 空间不足 | 下载前或激活前中止，明确显示所需空间 |
| SYNC-009 | Zip Slip 样本 | 拒绝快照，仓库根目录之外无新增文件 |
| SYNC-010 | ZIP Bomb/文件数超限 | 及时中止，无 OOM 或磁盘耗尽 |

### 7.4 Markdown 阅读

| ID | 场景 | 预期结果 |
|---|---|---|
| MD-001 | CommonMark 基线 | 内容结构和语义正确，无丢段落 |
| MD-002 | GFM 表格/任务列表 | 手机宽度下可读，宽表可横向查看 |
| MD-003 | 自动目录 | 层级正确，点击可定位，滚动时当前章节高亮 |
| MD-004 | 重复/中文标题 | anchor 稳定且定位正确 |
| MD-005 | 相对图片 | 当前仓库内正确加载，不跨仓库或越界 |
| MD-006 | 缺失图片 | 显示占位和替代文本，不破坏正文布局 |
| MD-007 | 跨文件相对链接 | 打开目标文档并支持返回原位置 |
| MD-008 | 当前/跨文件锚点 | 定位到正确标题 |
| MD-009 | 外部链接 | 交给系统浏览器前显示明确目标 |
| MD-010 | 代码块 | 长行横向滚动、可复制、深浅主题可读 |
| MD-011 | 深色模式切换 | 不重置阅读位置，无闪烁白屏 |
| MD-012 | 字体缩放 200% | 正文可读，导航和操作不被遮挡 |
| MD-013 | 屏幕旋转/进程重建 | 阅读进度恢复到合理位置 |
| MD-014 | 恶意内嵌 HTML | 脚本和危险链接不执行 |
| MD-015 | 5 MB 文档 | 在性能目标内打开，可滚动，无 ANR/OOM |
| MD-016 | 超过限制 | 提供明确降级或拒绝提示，不崩溃 |

### 7.5 收藏、进度和搜索

- 同名文件位于不同仓库时，收藏和进度互不影响。
- 文件更新但路径不变时保留阅读状态，并对无效进度做边界修正。
- 文件删除后不出现在正常搜索；孤立状态按保留策略清理。
- 搜索中文、英文、大小写、路径片段和特殊字符。
- 停用仓库不参与聚合搜索，但单仓库本地数据仍保留。

### 7.6 非 Markdown 文件

- HTML、PDF、XLSX 显示正确类型、大小和路径。
- 点击后可通过受控 `content://` URI 调用兼容系统应用。
- 系统无兼容应用时显示提示，不崩溃。
- 不支持文件不得被当作 Markdown 解析。

## 8. 性能验收

测试使用发布构建和中端真机，连续执行至少 5 次，报告中位数和 P95。

| 指标 | 目标 |
|---|---|
| 冷启动到可交互 | P95 < 2.5 秒 |
| 1,000 文档索引 | < 2 秒，不含网络和解压 |
| 10,000 文档搜索 | P95 < 500 ms |
| 1 MB Markdown 首屏 | P95 < 1 秒 |
| 5 MB Markdown 首屏 | P95 < 2 秒 |
| Markdown 连续滚动 | 无明显持续掉帧，关键交互无 ANR |
| 20 分钟阅读内存 | 无持续增长；退出阅读页后渲染资源可回收 |
| SHA 未变化同步 | 正常网络下 < 2 秒给出结果 |

## 9. 安全测试

- Access/refresh token 在 SharedPreferences、Room、文件、日志、崩溃堆栈和系统备份中不可明文发现。
- Device Flow 轮询遵守 `interval` 和 `slow_down`，取消或过期后停止轮询。
- GitHub App 未授权的私有仓库不可列出、不可通过手工 URL 绕过访问。
- 对归档路径执行规范化测试：绝对路径、`../`、编码变体、超长路径和符号链接。
- Markdown 清洗测试：script、iframe、object、事件属性、危险 scheme、CSS 远程资源。
- WebView 禁止任意文件访问和不必要的 JavaScript bridge。
- 外部文件通过只读、短生命周期 `content://` URI 分享。
- 私有仓库内容不得出现在公共外部存储和系统媒体索引中。

## 10. 无障碍与体验检查

- TalkBack 可读出仓库、同步状态、文档类型、收藏状态和主要按钮。
- 点击区域至少 48dp。
- 字体缩放 100%、150%、200% 下完成核心流程。
- 浅色、深色和高对比场景下正文、链接、代码和错误状态可辨识。
- 同步进度不只依赖颜色表达。
- 加载、空状态、错误和离线状态均有明确文字。

## 11. 自动化与 CI 门禁

每次 Pull Request 必须执行：

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

后续增加：

- Markdown parser fixture 快照测试。
- MockWebServer 同步契约测试。
- Room migration 测试。
- Compose 核心流程测试。
- Debug APK 构建产物和 Lint 报告归档。

CI 失败、测试不稳定或 Lint 新增 error 时禁止合并。因版本固定产生的已知 dependency warning 应记录原因，不能用全局关闭 Lint 处理。

## 12. 缺陷等级

| 等级 | 定义 | 示例 |
|---|---|---|
| Blocker | 无法发布或有数据/安全风险 | Token 泄漏、旧快照损坏、Zip Slip |
| Critical | 核心闭环不可用或高频崩溃 | 仓库无法同步、Markdown 无法打开 |
| Major | 核心体验明显受损但有绕行 | 大量相对图片失败、进度错误 |
| Minor | 局部视觉或低频问题 | 边缘语法样式不一致 |

## 13. 发布门禁

MVP 发布候选必须满足：

- Blocker、Critical 缺陷为 0。
- Major 缺陷均有明确评估，不得影响主要用户流程。
- P0/P1 用例全部通过，核心流程自动化连续 3 次稳定通过。
- Android 9、12、15、16 必测矩阵通过。
- Markdown fixture 基线、链接、图片、目录、主题和安全用例通过。
- GitHub App Device Flow、私有仓库选择、令牌刷新/撤销和只读权限审计通过。
- 飞行模式、下载中断、进程重建和空间不足回归通过。
- 性能指标达到目标或有经批准的偏差记录。
- Debug/Release 构建、单元测试和 Lint 无错误。

## 14. 测试输出物

- 测试执行报告和设备/系统版本清单。
- Markdown fixture 版本和失败差异截图。
- 自动化测试、Lint、性能和安全检查结果。
- 未解决缺陷列表、风险说明和发布建议。
