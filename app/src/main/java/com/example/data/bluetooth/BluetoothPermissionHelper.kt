package com.example.data.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothPermissionHelper {

  fun requiredPermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
    return if (sdkInt >= Build.VERSION_CODES.S) {
      listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
  }

  fun missingPermissions(
    context: Context,
    sdkInt: Int = Build.VERSION.SDK_INT,
  ): List<String> {
    return requiredPermissions(sdkInt).filter { permission ->
      ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }
  }

  fun hasScanPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return if (sdkInt >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    }
  }

  fun hasConnectPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return if (sdkInt >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun requiresLegacyLocationPermission(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return sdkInt < Build.VERSION_CODES.S
  }
}
