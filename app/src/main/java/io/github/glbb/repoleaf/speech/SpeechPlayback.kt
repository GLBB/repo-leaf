package io.github.glbb.repoleaf.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

enum class SpeechPlaybackStatus { Idle, Preparing, Playing, Paused, Completed, Failed }

data class SpeechUiState(
    val status: SpeechPlaybackStatus = SpeechPlaybackStatus.Idle,
    val documentId: String? = null,
    val title: String = "",
    val blockId: String? = null,
    val segmentIndex: Int = 0,
    val positionMs: Long = 0L,
    /** Audible time in this reading session. Buffering and pauses are deliberately excluded. */
    val elapsedPlaybackMs: Long = 0L,
    val speed: Float = 1f,
    val sleepMode: Boolean = false,
    val sleepTimerEndsAtMillis: Long? = null,
    val message: String? = null,
)

/**
 * Cloud synthesis is asynchronous. Keeping a small initial buffer prevents a short heading from
 * ending before the next cloud response is ready, while local engines retain their fast start.
 */
internal object SpeechQueuePlan {
    private const val CLOUD_INITIAL_BUFFER = 3

    fun initialSegmentCount(isCloudProvider: Boolean, remainingSegments: Int): Int =
        remainingSegments.coerceAtLeast(0).coerceAtMost(if (isCloudProvider) CLOUD_INITIAL_BUFFER else 1)
}

object SpeechUiStore {
    private val mutableState = MutableStateFlow(SpeechUiState())
    val state: StateFlow<SpeechUiState> = mutableState
    internal fun update(value: SpeechUiState) { mutableState.value = value }
}

object SpeechController {
    const val ACTION_PLAY_DOCUMENT = "io.github.glbb.repoleaf.speech.PLAY_DOCUMENT"
    const val ACTION_TOGGLE = "io.github.glbb.repoleaf.speech.TOGGLE"
    const val ACTION_STOP = "io.github.glbb.repoleaf.speech.STOP"
    const val ACTION_NEXT = "io.github.glbb.repoleaf.speech.NEXT"
    const val ACTION_PREVIOUS = "io.github.glbb.repoleaf.speech.PREVIOUS"
    const val ACTION_REWIND = "io.github.glbb.repoleaf.speech.REWIND"
    const val ACTION_FORWARD = "io.github.glbb.repoleaf.speech.FORWARD"
    const val ACTION_SET_SPEED = "io.github.glbb.repoleaf.speech.SET_SPEED"
    const val ACTION_SET_SLEEP_MODE = "io.github.glbb.repoleaf.speech.SET_SLEEP_MODE"
    const val ACTION_SET_SLEEP_TIMER = "io.github.glbb.repoleaf.speech.SET_SLEEP_TIMER"
    const val EXTRA_DOCUMENT_ID = "documentId"
    const val EXTRA_SOURCE_PATH = "sourcePath"
    const val EXTRA_TITLE = "title"
    const val EXTRA_VOICE_ID = "voiceId"
    const val EXTRA_SPEED = "speed"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_RESUME = "resume"

    /**
     * A fresh tap on a document's "朗读" button starts the whole document, like a reader.
     * Ongoing paused playback is resumed with ACTION_TOGGLE instead.
     */
    fun play(
        context: Context,
        documentId: String,
        sourcePath: String,
        title: String,
        voiceId: String? = null,
        resume: Boolean = false,
    ) {
        val intent = Intent(context, SpeechPlaybackService::class.java).setAction(ACTION_PLAY_DOCUMENT)
            .putExtra(EXTRA_DOCUMENT_ID, documentId).putExtra(EXTRA_SOURCE_PATH, sourcePath)
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_RESUME, resume)
        voiceId?.let { intent.putExtra(EXTRA_VOICE_ID, it) }
        ContextCompat.startForegroundService(context, intent)
    }

    fun send(context: Context, action: String, speed: Float? = null) {
        val intent = Intent(context, SpeechPlaybackService::class.java).setAction(action)
        speed?.let { intent.putExtra(EXTRA_SPEED, it) }
        context.startService(intent)
    }

    fun setSleepMode(context: Context, enabled: Boolean) {
        context.startService(
            Intent(context, SpeechPlaybackService::class.java)
                .setAction(ACTION_SET_SLEEP_MODE)
                .putExtra(EXTRA_ENABLED, enabled),
        )
    }

    fun setSleepTimer(context: Context, minutes: Int) {
        context.startService(
            Intent(context, SpeechPlaybackService::class.java)
                .setAction(ACTION_SET_SLEEP_TIMER)
                .putExtra(EXTRA_MINUTES, minutes),
        )
    }

    fun openSystemTtsSettings(context: Context) {
        val check = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val intent = if (check.resolveActivity(context.packageManager) != null) check else install
        runCatching { context.startActivity(intent) }
    }

    /** Opens an engine-owned configuration screen when the engine supplies one. */
    fun openTtsEngineSettings(context: Context, enginePackage: String) {
        val configure = Intent("android.speech.tts.engine.CONFIGURE_ENGINE")
            .setPackage(enginePackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (configure.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(configure) }
        } else {
            openSystemTtsSettings(context)
        }
    }
}

class SpeechPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var cache: SpeechCache
    private lateinit var progress: SpeechProgressStore
    private lateinit var preferences: SpeechPreferencesStore
    private var synthesisJob: Job? = null
    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var activeDocument: SpeechDocument? = null
    private var activeSourcePath: String = ""
    private var activeVoiceId: String = ""
    private var queuedSegments = mutableListOf<Int>()
    private var currentState = SpeechUiState()
    private var sessionAudibleMs = 0L
    private var audibleSinceElapsedRealtimeMs: Long? = null
    /** True only after every segment has been queued for playback. */
    private var generationFinished = true

    override fun onCreate() {
        super.onCreate()
        cache = SpeechCache(this)
        progress = SpeechProgressStore(this)
        preferences = SpeechPreferencesStore(this)
        createNotificationChannel()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateFromPlayer()
                override fun onIsPlayingChanged(isPlaying: Boolean) = updateFromPlayer()
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        if (generationFinished) markCompleted()
                        else update(currentState.copy(
                            status = SpeechPlaybackStatus.Preparing,
                            message = "正在准备下一段朗读…",
                        ))
                    } else updateFromPlayer()
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    update(currentState.copy(status = SpeechPlaybackStatus.Failed, message = "播放失败，请重试"))
                }
            })
        }
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SpeechController.ACTION_PLAY_DOCUMENT -> {
                val documentId = intent.getStringExtra(SpeechController.EXTRA_DOCUMENT_ID).orEmpty()
                val sourcePath = intent.getStringExtra(SpeechController.EXTRA_SOURCE_PATH).orEmpty()
                val title = intent.getStringExtra(SpeechController.EXTRA_TITLE).orEmpty()
                val voice = intent.getStringExtra(SpeechController.EXTRA_VOICE_ID)
                val resume = intent.getBooleanExtra(SpeechController.EXTRA_RESUME, false)
                if (documentId.isBlank() || sourcePath.isBlank()) update(SpeechUiState(status = SpeechPlaybackStatus.Failed, message = "无法读取要朗读的文档"))
                else startDocument(documentId, sourcePath, title, voice, resume)
            }
            SpeechController.ACTION_TOGGLE -> when {
                currentState.status == SpeechPlaybackStatus.Preparing -> stopPlayback()
                player.isPlaying -> player.pause()
                player.mediaItemCount > 0 -> player.play()
            }
            SpeechController.ACTION_NEXT -> player.seekToNextMediaItem()
            SpeechController.ACTION_PREVIOUS -> player.seekToPreviousMediaItem()
            SpeechController.ACTION_REWIND -> seekBy(-15_000)
            SpeechController.ACTION_FORWARD -> seekBy(15_000)
            SpeechController.ACTION_SET_SPEED -> intent.getFloatExtra(SpeechController.EXTRA_SPEED, 1f).let { setSpeed(it) }
            SpeechController.ACTION_SET_SLEEP_MODE -> setSleepMode(intent.getBooleanExtra(SpeechController.EXTRA_ENABLED, false))
            SpeechController.ACTION_SET_SLEEP_TIMER -> setSleepTimer(intent.getIntExtra(SpeechController.EXTRA_MINUTES, 0))
            SpeechController.ACTION_STOP -> stopPlayback()
        }
        return Service.START_NOT_STICKY
    }

    private fun startDocument(
        documentId: String,
        sourcePath: String,
        suppliedTitle: String,
        requestedVoice: String?,
        resume: Boolean,
    ) {
        synthesisJob?.cancel()
        generationFinished = false
        sessionAudibleMs = 0L
        audibleSinceElapsedRealtimeMs = null
        persistProgress()
        player.stop()
        player.clearMediaItems()
        queuedSegments.clear()
        startPreparingForeground(suppliedTitle.ifBlank { "正在准备朗读" })
        synthesisJob = serviceScope.launch {
            val source = File(sourcePath)
            if (!source.isFile) {
                update(SpeechUiState(status = SpeechPlaybackStatus.Failed, message = "离线文档已不存在"))
                return@launch
            }
            val document = withContext(Dispatchers.IO) { SpeechDocumentExtractor.fromFile(documentId, source) }
            if (document.segments.isEmpty()) {
                update(SpeechUiState(status = SpeechPlaybackStatus.Failed, message = "这篇文档没有可朗读的正文"))
                return@launch
            }
            val defaults = preferences.get()
            val saved = progress.get(documentId)?.takeIf { it.contentHash == document.contentHash }
            val startIndex = if (resume) saved?.segmentIndex?.coerceIn(0, document.segments.lastIndex) ?: 0 else 0
            val startPosition = if (resume) saved?.positionMs ?: 0L else 0L
            val speed = defaults.speed
            activeDocument = document
            activeSourcePath = sourcePath
            update(SpeechUiState(
                status = SpeechPlaybackStatus.Preparing,
                documentId = documentId,
                title = suppliedTitle.ifBlank { document.title },
                blockId = document.segments[startIndex].blockId,
                segmentIndex = startIndex,
                positionMs = startPosition,
                speed = speed,
                sleepMode = defaults.sleepMode,
            ))
            var engine: SpeechProvider? = null
            try {
                val selected: Pair<SpeechProvider, String> = if (defaults.mimoEnabled) {
                    val apiKey = MimoTtsCredentialStore(this@SpeechPlaybackService).getApiKey()
                        ?: throw IllegalStateException("MiMo API Key 未配置，请在朗读设置中保存后再试")
                    MimoTtsProvider(apiKey, defaults.sleepMode, defaults.mimoStyle) to MimoTts.voiceId(defaults.mimoVoice)
                } else {
                    val voices = LocalTtsCatalog.offlineChineseVoices(this@SpeechPlaybackService)
                    val voice = voices.firstOrNull { it.id == requestedVoice }
                        ?: voices.firstOrNull { it.id == defaults.voiceId }
                        ?: voices.firstOrNull { it.id == saved?.voiceId }
                        ?: voices.firstOrNull()
                        ?: throw IllegalStateException("未发现可用的中文离线音色，请在系统设置中安装语音数据")
                    LocalTtsEngine(this@SpeechPlaybackService, SpeechEngine(voice.enginePackage, voice.engineName)) to voice.id
                }
                val selectedEngine = selected.first
                val selectedVoiceId = selected.second
                engine = selectedEngine
                activeVoiceId = selectedVoiceId
                setPlaybackProfile(speed, defaults.sleepMode)
                setSleepTimer(defaults.sleepTimerMinutes)
                val initialBufferCount = SpeechQueuePlan.initialSegmentCount(
                    isCloudProvider = selectedEngine is MimoTtsProvider,
                    remainingSegments = document.segments.size - startIndex,
                )
                val firstIndices = (startIndex until startIndex + initialBufferCount).toList()
                firstIndices.forEachIndexed { bufferedCount, index ->
                    update(currentState.copy(
                        status = SpeechPlaybackStatus.Preparing,
                        message = if (initialBufferCount > 1) {
                            "正在缓冲朗读（${bufferedCount + 1}/$initialBufferCount）…"
                        } else {
                            "正在准备朗读…"
                        },
                    ))
                    synthesizeSegment(selectedEngine, document, index, selectedVoiceId)
                }
                if (!isActive) return@launch
                player.setMediaItems(firstIndices.map { mediaItem(document, it, selectedVoiceId) })
                queuedSegments.addAll(firstIndices)
                player.prepare()
                player.seekTo(0, startPosition)
                player.play()
                for (index in startIndex + firstIndices.size until document.segments.size) {
                    synthesizeSegment(selectedEngine, document, index, selectedVoiceId)
                    if (!isActive) return@launch
                    addGeneratedSegment(mediaItem(document, index, selectedVoiceId))
                    queuedSegments += index
                }
                generationFinished = true
                if (player.playbackState == Player.STATE_ENDED) markCompleted()
                cache.trimTo(protected = queuedSegments.map { cache.fileFor(document, selectedVoiceId, it) }.toSet())
            } catch (error: Exception) {
                if (isActive) update(currentState.copy(status = SpeechPlaybackStatus.Failed, message = error.message ?: "本地语音准备失败"))
            } finally {
                engine?.close()
            }
        }
        positionJob?.cancel()
        positionJob = serviceScope.launch {
            while (isActive) {
                delay(1_000)
                if (player.isPlaying) updateFromPlayer()
            }
        }
    }

    private suspend fun synthesizeSegment(engine: SpeechProvider, document: SpeechDocument, index: Int, voiceId: String) {
        val target = cache.fileFor(document, voiceId, index)
        if (cache.usable(target)) return
        val temp = cache.prepareTarget(target)
        temp.delete()
        withTimeout(SYNTHESIS_TIMEOUT_MS) {
            engine.synthesize(document.segments[index].text, voiceId, temp).getOrElse { throw it }
        }
        if (!cache.commit(temp, target)) throw IllegalStateException("语音缓存写入失败")
    }

    /**
     * A slow local engine can finish generating the next segment after ExoPlayer has already
     * reached the end of its queue. Add the segment and explicitly resume from that underflow.
     */
    private fun addGeneratedSegment(item: MediaItem) {
        val resumeAfterUnderflow = player.playbackState == Player.STATE_ENDED
        player.addMediaItem(item)
        if (resumeAfterUnderflow) {
            player.seekTo(player.mediaItemCount - 1, 0L)
            player.prepare()
            player.play()
        }
    }

    private fun markCompleted() {
        captureAudibleTime()
        update(currentState.copy(status = SpeechPlaybackStatus.Completed, positionMs = 0L, message = null))
        activeDocument?.let { progress.clear(it.documentId) }
    }

    private fun mediaItem(document: SpeechDocument, index: Int, voiceId: String): MediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(cache.fileFor(document, voiceId, index)))
        .setMediaId("${document.documentId}:$index")
        .setMediaMetadata(MediaMetadata.Builder().setTitle(document.title).build())
        .build()

    private fun setSpeed(value: Float) {
        val speed = SpeechPresets.supportedSpeeds.minBy { kotlin.math.abs(it - value) }
        preferences.put(preferences.get().copy(speed = speed))
        setPlaybackProfile(speed, currentState.sleepMode)
    }

    private fun setPlaybackProfile(speed: Float, sleepMode: Boolean) {
        val pitch = if (sleepMode) SpeechPresets.SLEEP_PITCH else 1f
        player.playbackParameters = PlaybackParameters(speed, pitch)
        update(currentState.copy(speed = speed, sleepMode = sleepMode))
    }

    private fun setSleepMode(enabled: Boolean) {
        val updated = preferences.get().copy(
            sleepMode = enabled,
            speed = if (enabled) SpeechPresets.SLEEP_SPEED else 1f,
        )
        preferences.put(updated)
        setPlaybackProfile(updated.speed, enabled)
    }

    private fun setSleepTimer(minutes: Int) {
        val normalized = minutes.takeIf(SpeechPresets.supportedSleepTimers::contains) ?: 0
        preferences.put(preferences.get().copy(sleepTimerMinutes = normalized))
        sleepTimerJob?.cancel()
        val deadline = SpeechPresets.timerDeadline(System.currentTimeMillis(), normalized)
        update(currentState.copy(sleepTimerEndsAtMillis = deadline))
        if (deadline != null) {
            sleepTimerJob = serviceScope.launch {
                delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
                stopPlayback()
            }
        }
    }

    private fun seekBy(deltaMs: Long) {
        if (player.mediaItemCount == 0) return
        val destination = player.currentPosition + deltaMs
        if (destination >= 0L) player.seekTo(destination)
        else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.seekToDefaultPosition()
        } else player.seekTo(0L)
        updateFromPlayer()
    }

    private fun updateFromPlayer() {
        val document = activeDocument ?: return
        captureAudibleTime()
        val localIndex = player.currentMediaItemIndex.coerceIn(0, queuedSegments.lastIndex.coerceAtLeast(0))
        val segmentIndex = queuedSegments.getOrNull(localIndex) ?: currentState.segmentIndex
        val status = when {
            player.isPlaying -> SpeechPlaybackStatus.Playing
            player.playbackState == Player.STATE_ENDED && !generationFinished -> SpeechPlaybackStatus.Preparing
            player.playbackState == Player.STATE_ENDED -> SpeechPlaybackStatus.Completed
            player.mediaItemCount > 0 -> SpeechPlaybackStatus.Paused
            else -> currentState.status
        }
        update(currentState.copy(
            status = status,
            documentId = document.documentId,
            title = currentState.title.ifBlank { document.title },
            segmentIndex = segmentIndex,
            blockId = document.segments.getOrNull(segmentIndex)?.blockId,
            positionMs = player.currentPosition.coerceAtLeast(0),
            elapsedPlaybackMs = sessionAudibleMs,
            speed = player.playbackParameters.speed,
            message = if (status == SpeechPlaybackStatus.Preparing) "正在缓冲下一段朗读…" else currentState.message,
        ))
        if (!player.isPlaying) persistProgress()
    }

    private fun update(value: SpeechUiState) {
        currentState = value
        SpeechUiStore.update(value)
    }

    private fun persistProgress() {
        val document = activeDocument ?: return
        progress.put(StoredSpeechProgress(
            documentId = document.documentId,
            sourcePath = activeSourcePath,
            title = currentState.title.ifBlank { document.title },
            contentHash = document.contentHash,
            voiceId = activeVoiceId,
            segmentIndex = currentState.segmentIndex,
            positionMs = currentState.positionMs,
            speed = currentState.speed,
        ))
    }

    /** Counts only the wall-clock intervals during which ExoPlayer is actually outputting audio. */
    private fun captureAudibleTime() {
        val now = SystemClock.elapsedRealtime()
        if (player.isPlaying) {
            audibleSinceElapsedRealtimeMs?.let { startedAt ->
                sessionAudibleMs += (now - startedAt).coerceAtLeast(0L)
            }
            audibleSinceElapsedRealtimeMs = now
        } else {
            audibleSinceElapsedRealtimeMs = null
        }
    }

    private fun stopPlayback() {
        captureAudibleTime()
        persistProgress()
        generationFinished = true
        synthesisJob?.cancel()
        sleepTimerJob?.cancel()
        player.stop()
        player.clearMediaItems()
        update(currentState.copy(status = SpeechPlaybackStatus.Idle))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startPreparingForeground(title: String) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(io.github.glbb.repoleaf.R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText("正在准备本地朗读")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "语音朗读", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) { persistProgress(); super.onTaskRemoved(rootIntent) }

    override fun onDestroy() {
        persistProgress()
        synthesisJob?.cancel()
        positionJob?.cancel()
        session.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "speech_playback"
        private const val NOTIFICATION_ID = 1024
        private const val SYNTHESIS_TIMEOUT_MS = 45_000L
    }
}
