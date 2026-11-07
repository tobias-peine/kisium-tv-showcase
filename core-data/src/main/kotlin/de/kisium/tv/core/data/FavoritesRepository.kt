package de.kisium.tv.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local, lightweight favorites store. IDs are stable Netzkino stream IDs. */
class FavoritesRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("kisium_favorites", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(read())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun isFavorite(id: String): Boolean = _ids.value.contains(id)

    fun toggle(id: String) {
        val next = _ids.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs.edit().putStringSet(KEY_IDS, next).apply()
        _ids.value = next
    }

    private fun read(): Set<String> =
        prefs.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()

    companion object {
        private const val KEY_IDS = "favorite_ids"
    }
}
