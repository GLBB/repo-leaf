package io.github.glbb.repoleaf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.glbb.repoleaf.app.RepoLeafViewModel
import io.github.glbb.repoleaf.domain.AppScreen
import io.github.glbb.repoleaf.domain.KnowledgeDocument
import io.github.glbb.repoleaf.domain.RepositoryConfig
import io.github.glbb.repoleaf.domain.ReadingState
import io.github.glbb.repoleaf.reader.MarkdownRenderer
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RepoLeafApp() }
    }
}

@Composable
private fun RepoLeafApp(vm: RepoLeafViewModel = viewModel()) {
    val state = vm.state
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error, state.status) {
        (state.error ?: state.status)?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    BackHandler(enabled = state.screen != AppScreen.Repositories) { vm.back() }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val screen = state.screen) {
                AppScreen.Repositories -> RepositoryScreen(
                    repositories = state.repositories,
                    documents = state.allDocuments,
                    readingStates = state.readingStates,
                    globalSearch = state.globalSearch,
                    libraryMode = state.libraryMode,
                    loading = state.loading,
                    snackbar = snackbar,
                    githubEnabled = vm.githubLoginAvailable,
                    onLogin = vm::beginGitHubLogin,
                    onAdd = vm::addRepository,
                    onOpen = vm::openRepository,
                    onSync = vm::sync,
                    onRemove = vm::removeRepository,
                    onGlobalSearch = vm::setGlobalSearch,
                    onLibraryMode = vm::setLibraryMode,
                    onOpenDocument = vm::openDocument,
                    onFavorite = vm::toggleFavorite,
                )
                is AppScreen.Documents -> DocumentScreen(
                    repository = state.repositories.firstOrNull { it.id == screen.repositoryId },
                    documents = state.documents,
                    search = state.search,
                    directory = state.documentDirectory,
                    loading = state.loading,
                    snackbar = snackbar,
                    onSearch = vm::setSearch,
                    onDirectory = vm::openDocumentDirectory,
                    onOpen = vm::openDocument,
                    onSync = { vm.sync(screen.repositoryId) },
                    onBack = { vm.back() },
                )
                is AppScreen.Reader -> ReaderScreen(
                    document = vm.currentDocument(),
                    documents = state.documents,
                    dark = state.darkReader,
                    scale = state.readerScale,
                    onBack = { vm.back() },
                    onTheme = vm::toggleReaderTheme,
                    onScale = vm::setReaderScale,
                    onLocalLink = vm::openRelativeDocument,
                    favorite = state.readingStates[vm.currentDocument()?.id]?.favorite == true,
                    initialScroll = state.readingStates[vm.currentDocument()?.id]?.scrollY ?: 0,
                    onFavorite = { vm.currentDocument()?.let(vm::toggleFavorite) },
                    onProgress = { value -> vm.currentDocument()?.let { vm.saveProgress(it, value) } },
                )
                AppScreen.GitHubLogin -> GitHubLoginScreen(state.deviceCode, state.loading, snackbar) { vm.back() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoryScreen(
    repositories: List<RepositoryConfig>, documents: List<KnowledgeDocument>, readingStates: Map<String, ReadingState>,
    globalSearch: String, libraryMode: String, loading: Boolean, snackbar: SnackbarHostState,
    githubEnabled: Boolean, onLogin: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit,
    onOpen: (String) -> Unit, onSync: (String) -> Unit, onRemove: (String) -> Unit,
    onGlobalSearch: (String) -> Unit, onLibraryMode: (String) -> Unit,
    onOpenDocument: (KnowledgeDocument) -> Unit, onFavorite: (KnowledgeDocument) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    val libraryDocuments = remember(documents, readingStates, globalSearch, libraryMode) {
        documents.asSequence().filter { document ->
            val reading = readingStates[document.id] ?: ReadingState()
            when (libraryMode) {
                "favorites" -> reading.favorite
                "recent" -> reading.lastOpenedAt != null
                else -> true
            }
        }.filter {
            globalSearch.isBlank() || it.title.contains(globalSearch, true) || it.relativePath.contains(globalSearch, true)
        }.let { sequence ->
            if (libraryMode == "recent") sequence.sortedByDescending { readingStates[it.id]?.lastOpenedAt ?: 0 }
            else sequence.sortedBy { it.title.lowercase() }
        }.toList()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("RepoLeaf", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp).testTag("repository-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("你的离线知识库", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("同步 GitHub 仓库，在手机上专注阅读 Markdown。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { showAdd = true }, enabled = !loading, modifier = Modifier.testTag("add-repository")) {
                        Text("添加仓库")
                    }
                    OutlinedButton(onClick = onLogin, enabled = githubEnabled && !loading) {
                        Text(if (githubEnabled) "连接 GitHub" else "GitHub App 未配置")
                    }
                }
                if (loading) LinearLoading()
                Spacer(Modifier.height(8.dp))
            }
            if (repositories.isEmpty()) {
                item { EmptyCard("还没有知识库", "添加公开仓库，或使用只读 Token 添加私有仓库。") }
            }
            items(repositories, key = { it.id }) { repository ->
                RepositoryCard(repository, onOpen, onSync, onRemove)
            }
            item {
                Text("资料库", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    globalSearch, onGlobalSearch, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("global-search"),
                    label = { Text("跨仓库搜索标题或路径") }, singleLine = true,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "全部", "recent" to "最近", "favorites" to "收藏").forEach { (mode, label) ->
                        if (libraryMode == mode) FilledTonalButton(onClick = { onLibraryMode(mode) }, modifier = Modifier.testTag("library-$mode")) { Text(label) }
                        else TextButton(onClick = { onLibraryMode(mode) }, modifier = Modifier.testTag("library-$mode")) { Text(label) }
                    }
                }
            }
            if (libraryDocuments.isEmpty()) {
                item { Text("暂无匹配文档", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            } else {
                items(libraryDocuments.take(100), key = { "library-${it.id}" }) { document ->
                    DocumentRow(document, readingStates[document.id]?.favorite == true, { onFavorite(document) }) { onOpenDocument(document) }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
    if (showAdd) AddRepositoryDialog(onDismiss = { showAdd = false }) { owner, repo, branch, root, token ->
        showAdd = false
        onAdd(owner, repo, branch, root, token)
    }
}

@Composable
private fun RepositoryCard(
    repository: RepositoryConfig, onOpen: (String) -> Unit, onSync: (String) -> Unit, onRemove: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(repository.id) },
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(repository.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text("${repository.branch}${repository.rootPath.takeIf { it.isNotEmpty() }?.let { " · /$it" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                repository.lastSyncedAt?.let { "上次同步：${DateFormat.getDateTimeInstance().format(Date(it))}" } ?: "尚未同步",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSync(repository.id) }) { Text("同步") }
                TextButton(onClick = { onOpen(repository.id) }) { Text("查看文档") }
                TextButton(onClick = { confirmDelete = true }) { Text("移除") }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("移除知识库？") },
        text = { Text("将删除这份仓库的离线快照和专用凭证，不会影响 GitHub 仓库。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onRemove(repository.id) }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}

@Composable
private fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit,
) {
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var root by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 GitHub 仓库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(owner, { owner = it }, label = { Text("所有者，例如 GLBB") }, singleLine = true, modifier = Modifier.testTag("owner"))
                OutlinedTextField(repo, { repo = it }, label = { Text("仓库名称") }, singleLine = true, modifier = Modifier.testTag("repo"))
                OutlinedTextField(branch, { branch = it }, label = { Text("分支") }, singleLine = true)
                OutlinedTextField(root, { root = it }, label = { Text("资料子目录（可选）") }, singleLine = true)
                OutlinedTextField(
                    token, { token = it }, label = { Text("Fine-grained Token（私有仓库，可选）") },
                    supportingText = { Text("仅需目标仓库 Contents: read；保存后由系统密钥加密。") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.testTag("token"),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(owner, repo, branch, root, token) }, enabled = owner.isNotBlank() && repo.isNotBlank(), modifier = Modifier.testTag("confirm-add")) { Text("添加并同步") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentScreen(
    repository: RepositoryConfig?, documents: List<KnowledgeDocument>, search: String, directory: String, loading: Boolean,
    snackbar: SnackbarHostState, onSearch: (String) -> Unit, onOpen: (KnowledgeDocument) -> Unit,
    onDirectory: (String) -> Unit, onSync: () -> Unit, onBack: () -> Unit,
) {
    val isSearching = search.isNotBlank()
    val filtered = remember(documents, search) {
        documents.filter { search.isBlank() || it.title.contains(search, true) || it.relativePath.contains(search, true) }
    }
    val folderEntries = remember(documents, directory) { immediateFolders(documents, directory) }
    val directDocuments = remember(documents, directory) { immediateDocuments(documents, directory) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(repository?.displayName ?: "文档") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ 返回") } },
                actions = { TextButton(onClick = onSync, enabled = !loading) { Text("同步") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                search, onSearch, modifier = Modifier.fillMaxWidth().testTag("document-search"),
                label = { Text(if (isSearching) "搜索结果（标题或路径）" else "在当前仓库搜索") }, singleLine = true,
            )
            if (!isSearching) DirectoryBreadcrumb(directory, onDirectory)
            if (loading) LinearLoading()
            if (isSearching && filtered.isEmpty() || !isSearching && folderEntries.isEmpty() && directDocuments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (documents.isEmpty()) "暂无 Markdown，请先同步仓库" else if (isSearching) "没有匹配的文档" else "这个目录暂无 Markdown")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp).testTag("document-list")) {
                    if (isSearching) {
                        items(filtered, key = { it.id }) { document -> DocumentRow(document, false, {}) { onOpen(document) } }
                    } else {
                        items(folderEntries, key = { "folder-${it.path}" }) { folder -> FolderRow(folder) { onDirectory(folder.path) } }
                        items(directDocuments, key = { it.id }) { document -> DocumentRow(document, false, {}) { onOpen(document) } }
                    }
                }
            }
        }
    }
}

private data class FolderEntry(val name: String, val path: String)

private fun immediateFolders(documents: List<KnowledgeDocument>, directory: String): List<FolderEntry> {
    val prefix = directory.trim('/').takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
    return documents.asSequence()
        .map { it.relativePath }
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix) }
        .filter { '/' in it }
        .map { it.substringBefore('/') }
        .distinct()
        .sortedBy { it.lowercase() }
        .map { FolderEntry(it, "$prefix$it") }
        .toList()
}

private fun immediateDocuments(documents: List<KnowledgeDocument>, directory: String): List<KnowledgeDocument> {
    val normalized = directory.trim('/')
    return documents.filter { it.relativePath.substringBeforeLast('/', "") == normalized }
        .sortedBy { it.title.lowercase() }
}

@Composable
private fun DirectoryBreadcrumb(directory: String, onDirectory: (String) -> Unit) {
    val segments = directory.split('/').filter { it.isNotBlank() }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onDirectory("") }, modifier = Modifier.testTag("directory-root")) { Text("根目录") }
        segments.forEachIndexed { index, segment ->
            Text("/")
            TextButton(onClick = { onDirectory(segments.take(index + 1).joinToString("/")) }) { Text(segment) }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderEntry, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 12.dp, horizontal = 4.dp).testTag("folder-item"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▸", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.padding(start = 12.dp)) {
            Text(folder.name, fontWeight = FontWeight.SemiBold)
            Text("文件夹", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DocumentRow(document: KnowledgeDocument, favorite: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 7.dp, horizontal = 4.dp).testTag("document-item"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(document.title, fontWeight = FontWeight.SemiBold)
            Text(document.relativePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onFavorite) { Text(if (favorite) "★" else "☆") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    document: KnowledgeDocument?, documents: List<KnowledgeDocument>, dark: Boolean, scale: Float,
    onBack: () -> Unit, onTheme: () -> Unit, onScale: (Float) -> Unit, onLocalLink: (java.io.File) -> Unit,
    favorite: Boolean, initialScroll: Int, onFavorite: () -> Unit, onProgress: (Int) -> Unit,
) {
    if (document == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("文档已不存在") }
        return
    }
    val context = LocalContext.current
    val repositoryRoot = remember(documents) { documents.firstOrNull()?.file?.let { first ->
        generateSequence(first.parentFile) { it.parentFile }.firstOrNull { candidate ->
            documents.all { doc -> doc.file.canonicalFile.toPath().startsWith(candidate.canonicalFile.toPath()) }
        }
    } ?: (document.file.parentFile ?: document.file) }
    val rendered = remember(document.file, dark, scale) { MarkdownRenderer.render(document.file, dark, scale) }
    val activeWebView = remember(document.id) { mutableStateOf<WebView?>(null) }
    val lastSavedScroll = remember(document.id) { intArrayOf(initialScroll) }
    DisposableEffect(document.id) {
        onDispose {
            activeWebView.value?.let {
                onProgress(it.scrollY)
                it.destroy()
            }
            activeWebView.value = null
        }
    }
    Scaffold(
        containerColor = if (dark) Color(0xFF101418) else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(rendered.title, maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ 返回") } },
                actions = {
                    TextButton(onClick = onFavorite, modifier = Modifier.testTag("reader-favorite")) { Text(if (favorite) "★" else "☆") }
                    TextButton(onClick = { onScale(scale - .1f) }) { Text("A−") }
                    TextButton(onClick = { onScale(scale + .1f) }) { Text("A+") }
                    TextButton(onClick = onTheme) { Text(if (dark) "浅色" else "深色") }
                },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("markdown-reader"),
            factory = { ctx ->
                WebView(ctx).apply {
                    activeWebView.value = this
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        if (kotlin.math.abs(scrollY - lastSavedScroll[0]) >= 64) {
                            lastSavedScroll[0] = scrollY
                            onProgress(scrollY)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.post { view.scrollTo(0, initialScroll) }
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return true
                            // Preserve in-page anchor navigation for the generated table of contents.
                            // The WebView can perform this without JavaScript when the base URL is the document.
                            if (uri.fragment != null) return false
                            if (uri.scheme == "file") {
                                val target = MarkdownRenderer.resolveLocalLink(document.file, repositoryRoot, uri.path.orEmpty())
                                target?.takeIf { it.extension.equals("md", true) }?.let(onLocalLink)
                                return true
                            }
                            if (uri.scheme in setOf("http", "https")) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                            return true
                        }
                    }
                }
            },
            update = { webView ->
                // update runs after every Compose recomposition, including progress saves.
                // Reload only when document appearance actually changes; otherwise scrolling resets to the top.
                if (webView.tag != rendered.html) {
                    webView.tag = rendered.html
                    webView.loadDataWithBaseURL(
                        document.file.canonicalFile.toURI().toString(),
                        rendered.html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitHubLoginScreen(code: io.github.glbb.repoleaf.domain.DeviceCode?, loading: Boolean, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("连接 GitHub") }, navigationIcon = { TextButton(onClick = onBack) { Text("‹ 返回") } }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            if (loading) CircularProgressIndicator()
            code?.let {
                Text("在 GitHub 输入验证码", style = MaterialTheme.typography.titleLarge)
                Text(it.userCode, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
                Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.verificationUri))) }) { Text("打开 GitHub 授权") }
                Text("授权后应用会自动完成登录。", modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun LinearLoading() = Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }

@Composable
private fun EmptyCard(title: String, description: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
