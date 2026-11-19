package com.example

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.bluetooth.BluetoothPermissionHelper
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.TwsPreferences
import com.example.domain.model.EarbudRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TWS Bridge", appName)
  }

  @Test
  fun `permission helper selects nearby devices on Android 12 plus`() {
    val permissions = BluetoothPermissionHelper.requiredPermissions(Build.VERSION_CODES.S)
    assertEquals(
      listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
      permissions,
    )
    assertFalse(BluetoothPermissionHelper.requiresLegacyLocationPermission(Build.VERSION_CODES.S))
  }

  @Test
  fun `permission helper selects fine location on Android 8 through 11`() {
    val permissions = BluetoothPermissionHelper.requiredPermissions(Build.VERSION_CODES.O)
    assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), permissions)
    assertTrue(BluetoothPermissionHelper.requiresLegacyLocationPermission(Build.VERSION_CODES.O))
  }

  @Test
  fun `dev mode toggle, theme mode, and device aliases persist across instances`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = TwsPreferences(context)
    assertFalse(prefs.devModeEnabled.value)
    assertEquals(ThemeMode.SYSTEM, prefs.themeMode.value)

    prefs.setDevModeEnabled(true)
    prefs.setThemeMode(ThemeMode.LIGHT)
    prefs.setCustomAlias("AA:BB:CC:DD:EE:FF", "Left Knockoff")
    prefs.setEarbudRole("AA:BB:CC:DD:EE:FF", EarbudRole.LEFT)

    val reloaded = TwsPreferences(context)
    assertTrue(reloaded.devModeEnabled.value)
    assertEquals(ThemeMode.LIGHT, reloaded.themeMode.value)
    assertEquals("Left Knockoff", reloaded.customAliases.value["AA:BB:CC:DD:EE:FF"])
    assertEquals(EarbudRole.LEFT, reloaded.earbudRoles.value["AA:BB:CC:DD:EE:FF"])
  }
}
