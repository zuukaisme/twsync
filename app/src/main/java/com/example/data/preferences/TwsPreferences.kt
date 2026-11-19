package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.model.EarbudRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
  SYSTEM("Auto"),
  LIGHT("Light"),
  DARK("Dark"),
}

class TwsPreferences(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _devModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEV_MODE, false))
  val devModeEnabled: StateFlow<Boolean> = _devModeEnabled.asStateFlow()

  private val _themeMode = MutableStateFlow(loadThemeMode())
  val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

  private val _customAliases = MutableStateFlow(loadAliases())
  val customAliases: StateFlow<Map<String, String>> = _customAliases.asStateFlow()

  private val _earbudRoles = MutableStateFlow(loadRoles())
  val earbudRoles: StateFlow<Map<String, EarbudRole>> = _earbudRoles.asStateFlow()

  fun setDevModeEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_DEV_MODE, enabled).apply()
    _devModeEnabled.value = enabled
  }

  fun setThemeMode(mode: ThemeMode) {
    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    _themeMode.value = mode
  }

  fun setCustomAlias(address: String, alias: String) {
    val trimmed = alias.trim()
    val key = "$PREFIX_ALIAS$address"
    if (trimmed.isEmpty()) {
      prefs.edit().remove(key).apply()
    } else {
      prefs.edit().putString(key, trimmed).apply()
    }
    _customAliases.value = loadAliases()
  }

  fun setEarbudRole(address: String, role: EarbudRole) {
    val key = "$PREFIX_ROLE$address"
    if (role == EarbudRole.NONE) {
      prefs.edit().remove(key).apply()
    } else {
      prefs.edit().putString(key, role.name).apply()
    }
    _earbudRoles.value = loadRoles()
  }

  private fun loadThemeMode(): ThemeMode {
    val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
    return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
  }

  private fun loadAliases(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    prefs.all.forEach { (k, v) ->
      if (k.startsWith(PREFIX_ALIAS) && v is String && v.isNotBlank()) {
        map[k.removePrefix(PREFIX_ALIAS)] = v
      }
    }
    return map
  }

  private fun loadRoles(): Map<String, EarbudRole> {
    val map = mutableMapOf<String, EarbudRole>()
    prefs.all.forEach { (k, v) ->
      if (k.startsWith(PREFIX_ROLE) && v is String) {
        val parsed = runCatching { EarbudRole.valueOf(v) }.getOrDefault(EarbudRole.NONE)
        if (parsed != EarbudRole.NONE) {
          map[k.removePrefix(PREFIX_ROLE)] = parsed
        }
      }
    }
    return map
  }

  companion object {
    private const val PREFS_NAME = "tws_bridge_os_prefs"
    private const val KEY_DEV_MODE = "dev_mode_enabled"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val PREFIX_ALIAS = "alias_"
    private const val PREFIX_ROLE = "role_"
  }
}
