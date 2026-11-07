package de.kisium.tv.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import de.kisium.tv.core.model.AppSettings
import de.kisium.tv.core.model.KisiumServerDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * Stores the Xtream password encrypted with Android Keystore + AES-256-GCM.
 *
 * Android 21/22 do not expose a stable AES keystore for this use case. On those
 * two API levels (neither target device uses them) the code falls back to a
 * private SharedPreferences file and reports [secureStorage] as false.
 */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences
    private val _secureStorage: Boolean

    private val _settings: MutableStateFlow<AppSettings>
    val settings: StateFlow<AppSettings>

    init {
        val result = createPreferences(context.applicationContext)
        prefs = result.first
        _secureStorage = result.second
        _settings = MutableStateFlow(read())
        settings = _settings.asStateFlow()
    }

    val current: AppSettings get() = _settings.value
    val secureStorage: Boolean get() = _secureStorage

    fun save(value: AppSettings) {
        prefs.edit()
            .putString(KEY_SERVER, value.serverUrl.trim())
            .putString(KEY_USER, value.username.trim())
            .putString(KEY_PASSWORD, value.password)
            .putString(KEY_TMDB, value.tmdbApiKey.trim())
            .putBoolean(KEY_AUTOPLAY, value.autoplayPreview)
            .putBoolean(KEY_DEMO, value.demoMode)
            .putBoolean(KEY_USE_JSON, value.useJsonCatalog)
            .putString(KEY_JSON_URI, value.catalogJsonUri.trim())
            .apply()
        _settings.value = value.copy(
            serverUrl = value.serverUrl.trim(),
            username = value.username.trim(),
            tmdbApiKey = value.tmdbApiKey.trim(),
            catalogJsonUri = value.catalogJsonUri.trim(),
        )
    }

    fun setAutoplayPreview(enabled: Boolean) {
        save(current.copy(autoplayPreview = enabled))
    }

    fun clearCredentials() {
        save(
            current.copy(
                serverUrl = "",
                username = "",
                password = "",
                demoMode = true,
            ),
        )
    }

    private fun read(): AppSettings {
        return AppSettings(
            serverUrl = prefs.getString(KEY_SERVER, KisiumServerDefaults.PROXY_BASE_URL)
                .orEmpty()
                .ifBlank { KisiumServerDefaults.PROXY_BASE_URL },
            username = prefs.getString(KEY_USER, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            tmdbApiKey = prefs.getString(KEY_TMDB, "") ?: "",
            autoplayPreview = prefs.getBoolean(KEY_AUTOPLAY, true),
            demoMode = prefs.getBoolean(KEY_DEMO, true),
            useJsonCatalog = prefs.getBoolean(KEY_USE_JSON, true),
            catalogJsonUri = prefs.getString(KEY_JSON_URI, KisiumServerDefaults.CATALOG_JSON_URI)
                .orEmpty()
                .ifBlank { KisiumServerDefaults.CATALOG_JSON_URI },
        )
    }

    private fun createPreferences(context: Context): Pair<SharedPreferences, Boolean> {
        val secure = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_SECURE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
        if (secure != null) return secure to true

        // Last-resort compatibility for API 21/22 or a broken vendor keystore.
        // Values are obfuscated, but this is explicitly NOT claimed as secure.
        val fallback = context.getSharedPreferences(FILE_FALLBACK, Context.MODE_PRIVATE)
        return ObfuscatedPreferences(fallback) to false
    }

    companion object {
        private const val FILE_SECURE = "kisium_secure_settings"
        private const val FILE_FALLBACK = "kisium_settings_compat"
        private const val KEY_SERVER = "server_url"
        private const val KEY_USER = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TMDB = "tmdb_key"
        private const val KEY_AUTOPLAY = "autoplay_preview"
        private const val KEY_DEMO = "demo_mode"
        private const val KEY_USE_JSON = "use_json_catalog"
        private const val KEY_JSON_URI = "catalog_json_uri"
    }

    /**
     * Compatibility wrapper. It only uses a reversible local obfuscation and is
     * selected only when EncryptedSharedPreferences cannot be initialised.
     */
    private class ObfuscatedPreferences(private val delegate: SharedPreferences) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = delegate.all
        override fun getString(key: String?, defValue: String?): String? {
            val value = delegate.getString(key, null) ?: return defValue
            return runCatching {
                String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8)
            }.getOrDefault(defValue ?: "")
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            delegate.getStringSet(key, defValues)

        override fun getInt(key: String?, defValue: Int): Int = delegate.getInt(key, defValue)
        override fun getLong(key: String?, defValue: Long): Long = delegate.getLong(key, defValue)
        override fun getFloat(key: String?, defValue: Float): Float = delegate.getFloat(key, defValue)
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = delegate.getBoolean(key, defValue)
        override fun contains(key: String?): Boolean = delegate.contains(key)
        override fun edit(): SharedPreferences.Editor = ObfuscatedEditor(delegate.edit())
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            delegate.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            delegate.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private class ObfuscatedEditor(private val delegate: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            delegate.putString(
                key,
                value?.let { Base64.encodeToString(it.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP) },
            )
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            delegate.putStringSet(key, values)
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            delegate.putInt(key, value)
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            delegate.putLong(key, value)
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            delegate.putFloat(key, value)
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            delegate.putBoolean(key, value)
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            delegate.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            delegate.clear()
            return this
        }

        override fun commit(): Boolean = delegate.commit()
        override fun apply() = delegate.apply()
    }
}
