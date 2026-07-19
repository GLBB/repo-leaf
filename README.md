# RepoLeaf

RepoLeaf 是一个面向 Android 的离线优先知识库阅读器。它计划连接多个 GitHub、GitLab、Gitea 或通用 Git 仓库，统一浏览 Markdown、HTML、PDF 和 XLSX 资料。

> Markdown-first MVP 已实现，可连接多个公开或私有 GitHub 仓库并离线阅读。

## 目标能力

- 配置和切换多个 Git 仓库
- 通过只读 GitHub App 授权同步 public/private repositories
- 仓库资料同步到本地，断网后仍可阅读
- Markdown 深度阅读；HTML、PDF、XLSX 在后续版本逐步加入内置渲染
- 文件树、跨仓库搜索、收藏和阅读进度
- 每个仓库独立的同步状态、分支和缓存策略

## 技术栈

- Kotlin
- Jetpack Compose / Material 3
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- JDK 17
- compileSdk 36 / minSdk 28

## 构建

安装 JDK 17、Android SDK 36 和 Build Tools 35.0.0，然后执行：

```powershell
.\gradlew.bat assembleDebug
```

也可以使用 Android Studio 打开项目并运行 `app`。

私有仓库可以在“添加仓库”时填写仅含 `Contents: read` 的 fine-grained token。正式 GitHub App Device Flow 入口需要在本地 Gradle 属性中配置：

```properties
REPOLEAF_GITHUB_CLIENT_ID=你的_GitHub_App_Client_ID
```

该值是 GitHub App 的公开 client ID；不要把 client secret 或访问令牌写入工程文件。

Windows 环境辅助脚本：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\setup-android.ps1
```

## 项目资料

- [MVP 产品方案](docs/mvp-product.md)
- [MVP 技术方案](docs/mvp-technical.md)
- [MVP 测试方案](docs/mvp-test-plan.md)
- [MVP 验证记录](docs/mvp-validation.md)
- [文档索引](docs/README.md)
- [同类项目调研](docs/research.md)
- [技术架构与迭代方案](docs/architecture.md)

## 当前进度

- [x] Android Compose 应用和多仓库管理
- [x] GitHub 同类项目调研
- [x] 技术架构与 MVP 边界
- [x] 多仓库配置和本地 Markdown 索引
- [x] GitHub 公开/私有仓库快照同步与离线缓存
- [x] Markdown、GFM、目录、内部链接、相对图片和明暗主题
- [x] 标题/路径搜索、字号调节和同步状态
- [x] Android Keystore 凭证加密与安全解压
- [x] 收藏、最近阅读、位置恢复和跨仓库标题/路径搜索
- [ ] HTML / PDF / XLSX 渲染器（Post-MVP）

## License

License 尚未确定。在许可证文件加入仓库前，不授予复制、修改或分发代码的许可。
