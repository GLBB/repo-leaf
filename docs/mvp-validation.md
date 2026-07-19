# RepoLeaf MVP 验证记录

验证日期：2026-07-19  
设备：RepoLeaf_API_35（Android 15，1080 × 2400，x86_64）

## 自动化门禁

| 验证项 | 命令/范围 | 结果 |
|---|---|---|
| JVM 单元测试 | `testDebugUnitTest` | 通过 |
| Debug APK | `assembleDebug` | 通过 |
| Android Lint | `lintDebug` | 通过 |
| 设备基础测试 | `connectedDebugAndroidTest` | 通过 |
| 公开仓库 E2E | `GLBB/repo-leaf`：鉴权、下载、索引、打开阅读器 | 通过 |
| 私有仓库 E2E | `GLBB/knowledge`：加密凭证、鉴权、下载、索引、打开阅读器 | 通过 |

## 已覆盖的关键风险

- GFM 表格、任务列表、删除线、目录和 YAML front matter 渲染。
- 相对 Markdown 链接解析与禁止越过仓库根目录。
- GitHub archive 重定向域名白名单，凭证不转发到下载域名。
- Zip Slip 拦截、文件数和解压总大小限制。
- Token 经 Android Keystore AES-GCM 加密后持久化，SharedPreferences 不含明文。
- 公开和私有仓库从 GitHub 网络同步到离线 Markdown 阅读的完整设备链路。
- 收藏、最近阅读、滚动位置持久化、收藏筛选和跨仓库标题/路径搜索。

真实网络 E2E 使用测试进程临时传入的现有 `gh` 会话凭证，执行结束后已清理应用数据；凭证未写入源码、Gradle 配置或验证记录。

## MVP 已知边界

- GitHub App Device Flow 代码已实现，但发布前仍需创建 RepoLeaf GitHub App，并通过本地 `REPOLEAF_GITHUB_CLIENT_ID` 配置完成产品级授权验收。
- MVP 内置渲染器仅支持 Markdown；HTML、PDF、XLSX 仍为 Post-MVP。
