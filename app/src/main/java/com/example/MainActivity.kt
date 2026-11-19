package com.example

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.bluetooth.BluetoothPermissionHelper
import com.example.data.preferences.ThemeMode
import com.example.ui.screens.InspectorScreen
import com.example.ui.screens.LabGuideScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.OsControlScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.TwsBridgeTheme
import com.example.ui.theme.TwsTheme
import com.example.ui.viewmodel.TwsBridgeViewModel

sealed class TwsRoute(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  data object OsHub :
    TwsRoute(
      route = "os_hub",
      labelRes = R.string.nav_os_hub,
      selectedIcon = Icons.Filled.Tune,
      unselectedIcon = Icons.Outlined.Tune,
    )

  data object Scanner :
    TwsRoute(
      route = "scanner",
      labelRes = R.string.nav_scanner,
      selectedIcon = Icons.Filled.BluetoothSearching,
      unselectedIcon = Icons.Outlined.BluetoothSearching,
    )

  data object Inspector :
    TwsRoute(
      route = "inspector",
      labelRes = R.string.nav_inspector,
      selectedIcon = Icons.Filled.DeveloperBoard,
      unselectedIcon = Icons.Outlined.DeveloperBoard,
    )

  data object Logs :
    TwsRoute(
      route = "logs",
      labelRes = R.string.nav_logs,
      selectedIcon = Icons.Filled.Terminal,
      unselectedIcon = Icons.Outlined.Terminal,
    )

  data object LabGuide :
    TwsRoute(
      route = "lab_guide",
      labelRes = R.string.nav_lab_guide,
      selectedIcon = Icons.Filled.MenuBook,
      unselectedIcon = Icons.Outlined.MenuBook,
    )
}

private val devRoutes =
  listOf(TwsRoute.OsHub, TwsRoute.Scanner, TwsRoute.Inspector, TwsRoute.Logs, TwsRoute.LabGuide)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: TwsBridgeViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      val systemDark = isSystemInDarkTheme()
      val useDarkTheme =
        when (themeMode) {
          ThemeMode.SYSTEM -> systemDark
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }

      TwsBridgeTheme(darkTheme = useDarkTheme) {
        TwsBridgeApp(
          viewModel = viewModel,
          isDarkTheme = useDarkTheme,
          onToggleTheme = { viewModel.toggleThemeMode(useDarkTheme) },
        )
      }
    }
  }
}

@Composable
fun TwsBridgeApp(
  viewModel: TwsBridgeViewModel,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val colors = TwsTheme.colors
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: TwsRoute.OsHub.route

  val devModeEnabled by viewModel.devModeEnabled.collectAsStateWithLifecycle()
  val environmentState by viewModel.environmentState.collectAsStateWithLifecycle()
  val localAdapterName by viewModel.localAdapterName.collectAsStateWithLifecycle()
  val audioState by viewModel.audioState.collectAsStateWithLifecycle()
  val osDevices by viewModel.bondedOrAudioDevices.collectAsStateWithLifecycle()
  val uiBannerMessage by viewModel.uiBannerMessage.collectAsStateWithLifecycle()

  val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
  val scanMode by viewModel.scanMode.collectAsStateWithLifecycle()
  val deviceFilter by viewModel.deviceFilter.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val devices by viewModel.filteredDevices.collectAsStateWithLifecycle()
  val totalDiscoveredCount by viewModel.allDevicesCount.collectAsStateWithLifecycle()
  val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
  val inspectionSession by viewModel.inspectionSession.collectAsStateWithLifecycle()
  val logs by viewModel.filteredLogs.collectAsStateWithLifecycle()
  val logFilter by viewModel.logFilter.collectAsStateWithLifecycle()

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
      viewModel.logPermissionOutcome(result)
    }

  val enableBluetoothLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
    ) {
      viewModel.refreshEnvironment()
    }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshEnvironment()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val navigateToRoute: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  // Keep user on OS Hub whenever Dev Mode is switched off
  LaunchedEffect(devModeEnabled) {
    if (!devModeEnabled && currentRoute != TwsRoute.OsHub.route) {
      navController.navigate(TwsRoute.OsHub.route) {
        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
      }
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      containerColor = colors.canvasBg,
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        TwsTopBar(
          devModeEnabled = devModeEnabled,
          isDarkTheme = isDarkTheme,
          onToggleTheme = onToggleTheme,
          onToggleDevMode = { enabled -> viewModel.setDevModeEnabled(enabled) },
        )
      },
      bottomBar = {
        AnimatedVisibility(visible = devModeEnabled && !useNavigationRail) {
          DevModeRectangularBottomBar(
            currentRoute = currentRoute,
            onSelectRoute = navigateToRoute,
          )
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        AnimatedVisibility(visible = devModeEnabled && useNavigationRail) {
          DevModeRectangularSideRail(
            currentRoute = currentRoute,
            onSelectRoute = navigateToRoute,
          )
        }

        NavHost(
          navController = navController,
          startDestination = TwsRoute.OsHub.route,
          modifier = Modifier.fillMaxSize(),
        ) {
          composable(TwsRoute.OsHub.route) {
            OsControlScreen(
              environmentState = environmentState,
              localAdapterName = localAdapterName,
              audioState = audioState,
              devices = osDevices,
              bannerMessage = uiBannerMessage,
              devModeEnabled = devModeEnabled,
              onDismissBanner = viewModel::dismissBanner,
              onRequestPermissions = {
                val missing = BluetoothPermissionHelper.missingPermissions(context)
                if (missing.isNotEmpty()) {
                  permissionLauncher.launch(missing.toTypedArray())
                }
              },
              onRequestEnableBluetooth = {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
              },
              onOpenSystemBluetoothSettings = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
              },
              onRenameLocalAdapter = viewModel::renameLocalOsBluetoothName,
              onSetMediaVolume = viewModel::setMediaVolume,
              onToggleMediaMute = viewModel::toggleMediaMute,
              onSetCallVolume = viewModel::setCallVolume,
              onSetRingVolume = viewModel::setRingVolume,
              onPlayChannelTest = viewModel::playChannelTest,
              onRenameDevice = viewModel::renameDevice,
              onAssignEarbudRole = viewModel::assignEarbudRole,
            )
          }

          composable(TwsRoute.Scanner.route) {
            ScannerScreen(
              environmentState = environmentState,
              isScanning = isScanning,
              scanMode = scanMode,
              deviceFilter = deviceFilter,
              searchQuery = searchQuery,
              devices = devices,
              totalDiscoveredCount = totalDiscoveredCount,
              onRequestPermissions = {
                val missing = BluetoothPermissionHelper.missingPermissions(context)
                if (missing.isNotEmpty()) {
                  permissionLauncher.launch(missing.toTypedArray())
                }
              },
              onRequestEnableBluetooth = {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
              },
              onScanModeSelected = viewModel::setScanMode,
              onToggleScan = viewModel::toggleScan,
              onFilterSelected = viewModel::setDeviceFilter,
              onSearchQueryChanged = viewModel::setSearchQuery,
              onInspectDevice = { device ->
                viewModel.selectDevice(device.address)
                navigateToRoute(TwsRoute.Inspector.route)
              },
            )
          }

          composable(TwsRoute.Inspector.route) {
            InspectorScreen(
              selectedDevice = selectedDevice,
              session = inspectionSession,
              onConnectGatt = viewModel::connectSelectedGatt,
              onDisconnectGatt = viewModel::disconnectGatt,
              onProbeClassicSdp = viewModel::probeSelectedSdp,
              onReadCharacteristic = viewModel::readCharacteristic,
            )
          }

          composable(TwsRoute.Logs.route) {
            LogsScreen(
              logs = logs,
              logFilter = logFilter,
              onFilterSelected = viewModel::setLogFilter,
              onShareLogs = {
                val shareIntent =
                  Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "TWS Bridge Diagnostic Log")
                    putExtra(Intent.EXTRA_TEXT, viewModel.exportLogsPlainText())
                  }
                context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Log"))
              },
              onClearLogs = viewModel::clearLogs,
            )
          }

          composable(TwsRoute.LabGuide.route) { LabGuideScreen() }
        }
      }
    }
  }
}

@Composable
private fun DevModeRectangularBottomBar(
  currentRoute: String,
  onSelectRoute: (String) -> Unit,
) {
  val colors = TwsTheme.colors

  Surface(
    color = colors.chromeBg,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.navigationBarsPadding()) {
      HorizontalDivider(color = colors.chromeBorder)
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        devRoutes.forEach { item ->
          val selected = currentRoute == item.route
          val label = stringResource(item.labelRes)
          Surface(
            onClick = { onSelectRoute(item.route) },
            shape = RoundedCornerShape(10.dp),
            color = if (selected) Color(0xFF0C354D) else colors.chromeSurface,
            border =
              BorderStroke(
                1.dp,
                if (selected) Color(0xFF38BDF8) else colors.chromeBorder,
              ),
            modifier =
              Modifier.weight(1f)
                .height(54.dp)
                .testTag("nav_${item.route}"),
          ) {
            Column(
              modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = label,
                tint =
                  if (selected) Color(0xFF38BDF8)
                  else colors.chromeOnSurface.copy(alpha = 0.72f),
                modifier = Modifier.size(19.dp),
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (selected) Color(0xFF38BDF8)
                  else colors.chromeOnSurface.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DevModeRectangularSideRail(
  currentRoute: String,
  onSelectRoute: (String) -> Unit,
) {
  val colors = TwsTheme.colors

  Surface(
    color = colors.chromeBg,
    modifier = Modifier.fillMaxHeight().width(96.dp),
  ) {
    Row(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        devRoutes.forEach { item ->
          val selected = currentRoute == item.route
          val label = stringResource(item.labelRes)
          Surface(
            onClick = { onSelectRoute(item.route) },
            shape = RoundedCornerShape(10.dp),
            color = if (selected) Color(0xFF0C354D) else colors.chromeSurface,
            border =
              BorderStroke(
                1.dp,
                if (selected) Color(0xFF38BDF8) else colors.chromeBorder,
              ),
            modifier = Modifier.fillMaxWidth().height(58.dp).testTag("nav_${item.route}"),
          ) {
            Column(
              modifier = Modifier.fillMaxSize().padding(4.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = label,
                tint =
                  if (selected) Color(0xFF38BDF8)
                  else colors.chromeOnSurface.copy(alpha = 0.72f),
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.height(3.dp))
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (selected) Color(0xFF38BDF8)
                  else colors.chromeOnSurface.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
      BoxDividerVertical(color = colors.chromeBorder)
    }
  }
}

@Composable
private fun BoxDividerVertical(color: Color) {
  Surface(color = color, modifier = Modifier.fillMaxHeight().width(1.dp)) {}
}

@Composable
private fun TwsTopBar(
  devModeEnabled: Boolean,
  isDarkTheme: Boolean,
  onToggleTheme: () -> Unit,
  onToggleDevMode: (Boolean) -> Unit,
) {
  val colors = TwsTheme.colors

  Surface(color = colors.chromeBg, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.statusBarsPadding()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = colors.chromeOnSurface,
          )
          Text(
            text =
              if (devModeEnabled) "Dev Mode • Diagnostics & GATT Lab"
              else "OS Audio & Device Control",
            style = MaterialTheme.typography.bodySmall,
            color =
              if (devModeEnabled) Color(0xFF38BDF8)
              else colors.chromeOnSurface.copy(alpha = 0.7f),
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          IconButton(
            onClick = onToggleTheme,
            modifier = Modifier.testTag("theme_mode_toggle"),
          ) {
            Icon(
              imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
              contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
              tint = colors.chromeOnSurface,
            )
          }

          Surface(
            onClick = { onToggleDevMode(!devModeEnabled) },
            color = if (devModeEnabled) Color(0xFF0C354D) else colors.chromeSurface,
            shape = RoundedCornerShape(10.dp),
            border =
              BorderStroke(
                1.dp,
                if (devModeEnabled) Color(0xFF38BDF8).copy(alpha = 0.7f) else colors.chromeBorder,
              ),
            modifier = Modifier.testTag("dev_mode_toggle_card"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = stringResource(R.string.dev_mode_label),
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (devModeEnabled) Color(0xFF38BDF8)
                  else colors.chromeOnSurface.copy(alpha = 0.8f),
              )
              Switch(
                checked = devModeEnabled,
                onCheckedChange = onToggleDevMode,
                colors =
                  SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF38BDF8),
                    checkedTrackColor = Color(0xFF08273A),
                    uncheckedThumbColor = colors.chromeOnSurface.copy(alpha = 0.7f),
                    uncheckedTrackColor = colors.chromeBg,
                    uncheckedBorderColor = colors.chromeBorder,
                  ),
                modifier = Modifier.size(width = 44.dp, height = 28.dp).testTag("dev_mode_toggle"),
              )
            }
          }
        }
      }
      HorizontalDivider(color = colors.chromeBorder)
    }
  }
}
