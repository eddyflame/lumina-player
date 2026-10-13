package org.lumina.player.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class DarkThemeStyle {
    SLATE,
    AMOLED
}

data class ThemeSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val darkThemeStyle: DarkThemeStyle = DarkThemeStyle.SLATE,
    val dynamicColor: Boolean = false
)

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeSettings = MutableStateFlow(loadSettings())
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    private fun loadSettings(): ThemeSettings {
        val modeStr = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val styleStr = prefs.getString(KEY_DARK_STYLE, DarkThemeStyle.SLATE.name) ?: DarkThemeStyle.SLATE.name
        val dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)

        val mode = runCatching { AppThemeMode.valueOf(modeStr) }.getOrDefault(AppThemeMode.SYSTEM)
        val style = runCatching { DarkThemeStyle.valueOf(styleStr) }.getOrDefault(DarkThemeStyle.SLATE)

        return ThemeSettings(themeMode = mode, darkThemeStyle = style, dynamicColor = dynamicColor)
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeSettings.value = _themeSettings.value.copy(themeMode = mode)
    }

    fun setDarkThemeStyle(style: DarkThemeStyle) {
        prefs.edit().putString(KEY_DARK_STYLE, style.name).apply()
        _themeSettings.value = _themeSettings.value.copy(darkThemeStyle = style)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(dynamicColor = enabled)
    }

    companion object {
        private const val PREFS_NAME = "lumina_theme_prefs"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_DARK_STYLE = "key_dark_style"
        private const val KEY_DYNAMIC_COLOR = "key_dynamic_color"
    }
}
