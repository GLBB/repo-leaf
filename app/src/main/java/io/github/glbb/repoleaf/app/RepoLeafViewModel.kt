package io.github.glbb.repoleaf.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.glbb.repoleaf.BuildConfig
import io.github.glbb.repoleaf.data.CredentialStore
import io.github.glbb.repoleaf.data.GitHubClient
import io.github.glbb.repoleaf.data.RepositoryStore
import io.github.glbb.repoleaf.data.ReadingStateStore
import io.github.glbb.repoleaf.data.SnapshotSync
import io.github.glbb.repoleaf.data.SnapshotSync.Companion.ACCOUNT_TOKEN
import io.github.glbb.repoleaf.data.SnapshotSync.Companion.repositoryTokenAlias
import io.github.glbb.repoleaf.domain.AppScreen
import io.github.glbb.repoleaf.domain.DeviceCode
import io.github.glbb.repoleaf.domain.DeviceTokenResult
import io.github.glbb.repoleaf.domain.KnowledgeDocument
import io.github.glbb.repoleaf.domain.RepositoryConfig
import io.github.glbb.repoleaf.domain.ReadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val repositories: List<RepositoryConfig> = emptyList(),
    val allDocuments: List<KnowledgeDocument> = emptyList(),
    val readingStates: Map<String, ReadingState> = emptyMap(),
    val documents: List<KnowledgeDocument> = emptyList(),
    val screen: AppScreen = AppScreen.Repositories,
    val loading: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val search: String = "",
    val documentDirectory: String = "",
    val globalSearch: String = "",
    val libraryMode: String = "all",
    val githubLogin: String? = null,
    val deviceCode: DeviceCode? = null,
    val darkReader: Boolean = false,
    val readerScale: Float = 1f,
)

class RepoLeafViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RepositoryStore(application)
    private val credentials = CredentialStore(application)
    private val reading = ReadingStateStore(application)
    private val github = GitHubClient()
    private val sync = SnapshotSync(store, github, credentials)

    var state by mutableStateOf(AppUiState(repositories = store.repositories()))
        private set

    init { refreshLibrary() }

    val githubLoginAvailable: Boolean get() = BuildConfig.GITHUB_CLIENT_ID.isNotBlank()

    fun addRepository(owner: String, name: String, branch: String, rootPath: String, token: String) {
        val config = RepositoryConfig(
            owner = owner.trim(), name = name.trim(), branch = branch.trim().ifBlank { "main" },
            rootPath = rootPath.trim().trim('/'),
        )
        if (config.owner.isBlank() || config.name.isBlank()) {
            state = state.copy(error = "请输入仓库所有者和名称")
            return
        }
        store.save(config)
        if (token.isNotBlank()) credentials.put(repositoryTokenAlias(config.id), token.trim())
        refreshRepositories()
        sync(config.id)
    }

    fun removeRepository(id: String) {
        credentials.remove(repositoryTokenAlias(id))
        store.remove(id)
        refreshRepositories()
        refreshLibrary()
    }

    fun sync(id: String) = viewModelScope.launch {
        val config = store.repositories().firstOrNull { it.id == id } ?: return@launch
        state = state.copy(loading = true, error = null, status = "正在同步 ${config.displayName}…")
        runCatching { withContext(Dispatchers.IO) { sync.sync(config) } }
            .onSuccess { (updated, result) ->
                refreshRepositories()
                refreshLibrary()
                val documents = store.documents(updated)
                state = state.copy(
                    loading = false, documents = documents,
                    screen = AppScreen.Documents(id),
                    documentDirectory = "",
                    status = if (result.unchanged) "已是最新版本" else "同步完成：${documents.size} 篇 Markdown",
                )
            }
            .onFailure { state = state.copy(loading = false, status = null, error = friendlyError(it)) }
    }

    fun openRepository(id: String) {
        val config = store.repositories().firstOrNull { it.id == id } ?: return
        state = state.copy(
            screen = AppScreen.Documents(id), documents = store.documents(config), search = "", error = null,
            documentDirectory = "",
        )
    }

    fun openDocument(document: KnowledgeDocument) {
        val updated = reading.get(document.id).copy(lastOpenedAt = System.currentTimeMillis())
        reading.put(document.id, updated)
        state = state.copy(
            documents = store.repositories().firstOrNull { it.id == document.repositoryId }?.let(store::documents) ?: state.documents,
            readingStates = state.readingStates + (document.id to updated),
            screen = AppScreen.Reader(document.id), error = null,
        )
    }

    fun currentDocument(): KnowledgeDocument? = when (val screen = state.screen) {
        is AppScreen.Reader -> state.documents.firstOrNull { it.id == screen.documentId }
        else -> null
    }

    fun openRelativeDocument(file: java.io.File) {
        state.documents.firstOrNull { it.file.canonicalFile == file.canonicalFile }?.let(::openDocument)
            ?: run { state = state.copy(error = "目标 Markdown 不在当前知识库索引中") }
    }

    fun setSearch(value: String) { state = state.copy(search = value) }
    fun openDocumentDirectory(path: String) { state = state.copy(documentDirectory = path.trim('/')) }
    fun setGlobalSearch(value: String) { state = state.copy(globalSearch = value) }
    fun setLibraryMode(value: String) { state = state.copy(libraryMode = value) }
    fun toggleFavorite(document: KnowledgeDocument) {
        val updated = reading.get(document.id).let { it.copy(favorite = !it.favorite) }
        reading.put(document.id, updated)
        state = state.copy(readingStates = state.readingStates + (document.id to updated))
    }
    fun saveProgress(document: KnowledgeDocument, scrollY: Int) {
        val updated = reading.get(document.id).copy(scrollY = scrollY.coerceAtLeast(0))
        reading.put(document.id, updated)
        state = state.copy(readingStates = state.readingStates + (document.id to updated))
    }
    fun setReaderScale(value: Float) { state = state.copy(readerScale = value.coerceIn(.8f, 1.5f)) }
    fun toggleReaderTheme() { state = state.copy(darkReader = !state.darkReader) }
    fun clearMessage() { state = state.copy(error = null, status = null) }

    fun back(): Boolean {
        state = when (val screen = state.screen) {
            is AppScreen.Reader -> state.copy(screen = AppScreen.Documents(screen.documentId.substringBefore(':')))
            is AppScreen.Documents -> {
                if (state.documentDirectory.isNotBlank()) {
                    state.copy(documentDirectory = state.documentDirectory.substringBeforeLast('/', ""))
                } else {
                    state.copy(screen = AppScreen.Repositories, documents = emptyList())
                }
            }
            AppScreen.GitHubLogin -> state.copy(screen = AppScreen.Repositories, documents = emptyList())
            AppScreen.Repositories -> return false
        }
        return true
    }

    fun beginGitHubLogin() = viewModelScope.launch {
        if (!githubLoginAvailable) {
            state = state.copy(error = "构建时尚未配置 REPOLEAF_GITHUB_CLIENT_ID")
            return@launch
        }
        state = state.copy(screen = AppScreen.GitHubLogin, loading = true, error = null, status = "正在获取登录验证码…")
        runCatching { withContext(Dispatchers.IO) { github.requestDeviceCode(BuildConfig.GITHUB_CLIENT_ID) } }
            .onSuccess { code ->
                state = state.copy(loading = false, deviceCode = code, status = "请在 GitHub 输入验证码")
                pollGitHubToken(code)
            }
            .onFailure { state = state.copy(loading = false, error = friendlyError(it), status = null) }
    }

    private fun pollGitHubToken(code: DeviceCode) = viewModelScope.launch {
        var interval = code.intervalSeconds
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000
        while (isActive && System.currentTimeMillis() < deadline && state.screen == AppScreen.GitHubLogin) {
            delay(interval * 1000)
            when (val result = withContext(Dispatchers.IO) { github.pollDeviceToken(BuildConfig.GITHUB_CLIENT_ID, code) }) {
                is DeviceTokenResult.Pending -> interval = result.nextIntervalSeconds
                is DeviceTokenResult.Failure -> {
                    state = state.copy(error = result.message, status = null)
                    return@launch
                }
                is DeviceTokenResult.Success -> {
                    credentials.put(ACCOUNT_TOKEN, result.accessToken)
                    val login = runCatching { withContext(Dispatchers.IO) { github.currentLogin(result.accessToken) } }.getOrNull()
                    state = state.copy(
                        screen = AppScreen.Repositories, githubLogin = login, deviceCode = null,
                        status = login?.let { "已连接 GitHub：$it" } ?: "GitHub 已连接",
                    )
                    return@launch
                }
            }
        }
    }

    private fun refreshRepositories() { state = state.copy(repositories = store.repositories()) }

    private fun refreshLibrary() {
        val documents = store.repositories().flatMap(store::documents)
        state = state.copy(
            allDocuments = documents,
            readingStates = documents.associate { it.id to reading.get(it.id) },
        )
    }

    private fun friendlyError(error: Throwable): String =
        generateSequence(error) { it.cause }.mapNotNull { it.message }.firstOrNull { it.isNotBlank() }
            ?: "操作失败，请稍后重试"
}
