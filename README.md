# RepoLeaf

RepoLeaf 是一个面向 Android 的离线优先知识库阅读器。它计划连接多个 GitHub、GitLab、Gitea 或通用 Git 仓库，统一浏览 Markdown、HTML、PDF 和 XLSX 资料。

> 当前处于早期 MVP 阶段。

## 目标能力

- 配置和切换多个 Git 仓库
- 仓库资料同步到本地，断网后仍可阅读
- Markdown、HTML、PDF、XLSX 统一阅读入口
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

Windows 环境辅助脚本：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\setup-android.ps1
```

## 项目资料

- [同类项目调研](docs/research.md)
- [技术架构与迭代方案](docs/architecture.md)

## 当前进度

- [x] Android Compose 工程和首页体验稿
- [x] GitHub 同类项目调研
- [x] 技术架构与 MVP 边界
- [ ] 多仓库配置和本地索引
- [ ] GitHub 仓库快照同步
- [ ] Markdown / HTML 渲染器
- [ ] PDF / XLSX 渲染器
- [ ] 搜索、收藏和阅读进度

## License

License 尚未确定。在许可证文件加入仓库前，不授予复制、修改或分发代码的许可。
