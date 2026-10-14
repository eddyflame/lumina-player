package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.lumina.player.data.preferences.AppThemeMode
import org.lumina.player.data.preferences.DarkThemeStyle
import org.lumina.player.data.preferences.LockscreenControlStyle
import org.lumina.player.data.preferences.ThemeSettings

class ThemeSettingsTest {

    @Test
    fun testLockscreenControlStyleEnumValues() {
        val values = LockscreenControlStyle.values()
        assertEquals(2, values.size)
        assertEquals(LockscreenControlStyle.LUMINA_COMPACT, LockscreenControlStyle.valueOf("LUMINA_COMPACT"))
        assertEquals(LockscreenControlStyle.SYSTEM_MEDIA, LockscreenControlStyle.valueOf("SYSTEM_MEDIA"))
    }

    @Test
    fun testThemeSettingsDefaultLockscreenControlStyle() {
        val settings = ThemeSettings()
        assertEquals(AppThemeMode.SYSTEM, settings.themeMode)
        assertEquals(DarkThemeStyle.SLATE, settings.darkThemeStyle)
        assertEquals(false, settings.dynamicColor)
        assertEquals(LockscreenControlStyle.LUMINA_COMPACT, settings.lockscreenControlStyle)
    }

    @Test
    fun testThemeSettingsCopyLockscreenControlStyle() {
        val settings = ThemeSettings()
        val updated = settings.copy(lockscreenControlStyle = LockscreenControlStyle.SYSTEM_MEDIA)
        assertEquals(LockscreenControlStyle.SYSTEM_MEDIA, updated.lockscreenControlStyle)
    }

    @Test
    fun testLockscreenStyleFallbackParsing() {
        val invalidStyleStr = "INVALID_STYLE_NAME"
        val parsed = runCatching { LockscreenControlStyle.valueOf(invalidStyleStr) }
            .getOrDefault(LockscreenControlStyle.LUMINA_COMPACT)
        assertEquals(LockscreenControlStyle.LUMINA_COMPACT, parsed)

        val validStyleStr = LockscreenControlStyle.SYSTEM_MEDIA.name
        val parsedValid = runCatching { LockscreenControlStyle.valueOf(validStyleStr) }
            .getOrDefault(LockscreenControlStyle.LUMINA_COMPACT)
        assertEquals(LockscreenControlStyle.SYSTEM_MEDIA, parsedValid)
    }
}
