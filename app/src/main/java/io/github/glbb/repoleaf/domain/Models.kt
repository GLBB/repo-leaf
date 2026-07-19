package io.github.glbb.repoleaf.domain

import java.io.File
import java.util.UUID

data class RepositoryConfig(
    val id: String = UUID.randomUUID().toString(),
    val owner: String,
    val name: String,
    val branch: String = "main",
    val rootPath: String = "",
    val displayName: String = "$owner/$name",
    val activeCommit: String? = null,
    val lastSyncedAt: Long? = null,
)

data class KnowledgeDocument(
    val repositoryId: String,
    val relativePath: String,
    val title: String,
    val file: File,
) {
    val id: String get() = "$repositoryId:$relativePath"
}

data class SyncResult(
    val commit: String,
    val documents: List<KnowledgeDocument>,
    val unchanged: Boolean,
)

data class ReadingState(
    val favorite: Boolean = false,
    val lastOpenedAt: Long? = null,
    val scrollY: Int = 0,
)

sealed interface AppScreen {
    data object Repositories : AppScreen
    data class Documents(val repositoryId: String) : AppScreen
    data class Reader(val documentId: String) : AppScreen
    data object GitHubLogin : AppScreen
}

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

sealed interface DeviceTokenResult {
    data class Success(val accessToken: String) : DeviceTokenResult
    data class Pending(val nextIntervalSeconds: Long) : DeviceTokenResult
    data class Failure(val message: String) : DeviceTokenResult
}
