package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.AppThemeMode
import com.example.core.ThemePreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemePreferencesTest {

    @Test
    fun `themeMode defaults to SYSTEM and persists Light Dark and System across manager instances`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val writer = ThemePreferencesManager(context)

        assertEquals(AppThemeMode.SYSTEM, writer.themeModeFlow.first())

        writer.setThemeMode(AppThemeMode.LIGHT)
        val afterLightReader = ThemePreferencesManager(context)
        assertEquals(AppThemeMode.LIGHT, afterLightReader.themeModeFlow.first())

        writer.setThemeMode(AppThemeMode.DARK)
        val afterDarkReader = ThemePreferencesManager(context)
        assertEquals(AppThemeMode.DARK, afterDarkReader.themeModeFlow.first())

        writer.setThemeMode(AppThemeMode.SYSTEM)
        val afterSystemReader = ThemePreferencesManager(context)
        assertEquals(AppThemeMode.SYSTEM, afterSystemReader.themeModeFlow.first())
    }

    @Test
    fun `fromStorageKey handles unknown or null values safely`() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey("invalid"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStorageKey("LIGHT"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStorageKey("dark"))
    }
}
