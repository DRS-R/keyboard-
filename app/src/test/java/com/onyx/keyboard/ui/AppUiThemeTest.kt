package com.onyx.keyboard.ui

import com.onyx.keyboard.data.Prefs
import org.junit.Assert.*
import org.junit.Test

class AppUiThemeTest {

    @Test
    fun testThemeModeConstants() {
        assertEquals("THEME_MODE_SYSTEM must be 0", 0, Prefs.THEME_MODE_SYSTEM)
        assertEquals("THEME_MODE_LIGHT must be 1", 1, Prefs.THEME_MODE_LIGHT)
        assertEquals("THEME_MODE_DARK must be 2", 2, Prefs.THEME_MODE_DARK)
    }

    @Test
    fun testCreateStaticDarkTheme() {
        val darkScheme = AppUiTheme.createStatic(isDark = true)
        assertTrue("isDark must be true for dark theme", darkScheme.isDark)
        assertFalse("isDynamic must be false for static theme", darkScheme.isDynamic)
        assertNotNull("Primary color should not be null", darkScheme.primary)
        assertNotNull("Background color should not be null", darkScheme.background)
        assertNotNull("Card background should not be null", darkScheme.cardBackground)
    }

    @Test
    fun testCreateStaticLightTheme() {
        val lightScheme = AppUiTheme.createStatic(isDark = false)
        assertFalse("isDark must be false for light theme", lightScheme.isDark)
        assertFalse("isDynamic must be false for static theme", lightScheme.isDynamic)
        assertNotNull("Primary color should not be null", lightScheme.primary)
        assertNotNull("Background color should not be null", lightScheme.background)
        assertNotNull("Card background should not be null", lightScheme.cardBackground)
    }

    @Test
    fun testSchemeContrastIntegrity() {
        val dark = AppUiTheme.createStatic(isDark = true)
        val light = AppUiTheme.createStatic(isDark = false)

        assertNotEquals("Dark background and Light background must differ", dark.background, light.background)
        assertNotEquals("Dark primary and Light primary must differ", dark.primary, light.primary)
        assertNotEquals("Dark card background and Light card background must differ", dark.cardBackground, light.cardBackground)
    }
}
