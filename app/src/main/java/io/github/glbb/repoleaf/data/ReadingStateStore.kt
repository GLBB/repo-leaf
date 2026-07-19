package io.github.glbb.repoleaf.data

import android.content.Context
import io.github.glbb.repoleaf.domain.ReadingState
import org.json.JSONObject

class ReadingStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("reading_state", Context.MODE_PRIVATE)

    fun get(documentId: String): ReadingState {
        val value = prefs.getString(documentId, null) ?: return ReadingState()
        return runCatching {
            JSONObject(value).let {
                ReadingState(
                    favorite = it.optBoolean("favorite"),
                    lastOpenedAt = if (it.has("lastOpenedAt")) it.optLong("lastOpenedAt") else null,
                    scrollY = it.optInt("scrollY"),
                )
            }
        }.getOrDefault(ReadingState())
    }

    fun put(documentId: String, state: ReadingState) {
        val value = JSONObject().put("favorite", state.favorite)
            .put("lastOpenedAt", state.lastOpenedAt).put("scrollY", state.scrollY)
        prefs.edit().putString(documentId, value.toString()).apply()
    }
}
