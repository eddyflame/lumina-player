package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lumina.player.data.preferences.AppThemeMode
import org.lumina.player.data.preferences.DarkThemeStyle
import org.lumina.player.data.preferences.ThemeSettings

class ThemeSettingsTest {

    @Test
    fun testThemeSettingsDefaults() {
        val settings = ThemeSettings()
        assertEquals(AppThemeMode.SYSTEM, settings.themeMode)
        assertEquals(DarkThemeStyle.SLATE, settings.darkThemeStyle)
        assertEquals(false, settings.dynamicColor)
    }

    @Test
    fun testThemeSettingsCustomValues() {
        val settings = ThemeSettings(
            themeMode = AppThemeMode.DARK,
            darkThemeStyle = DarkThemeStyle.AMOLED,
            dynamicColor = true
        )
        assertEquals(AppThemeMode.DARK, settings.themeMode)
        assertEquals(DarkThemeStyle.AMOLED, settings.darkThemeStyle)
        assertEquals(true, settings.dynamicColor)
    }

    @Test
    fun testThemeSettingsCopy() {
        val settings = ThemeSettings()
        val updated = settings.copy(darkThemeStyle = DarkThemeStyle.AMOLED)
        assertEquals(DarkThemeStyle.AMOLED, updated.darkThemeStyle)
        assertEquals(AppThemeMode.SYSTEM, updated.themeMode)
    }
}
