package dev.russell.fatebook.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a function that asks for POST_NOTIFICATIONS on Android 13+ and
 * reports the outcome through [onResult]. On older versions, or when already
 * granted, [onResult] is called with true immediately. Shared by Settings and
 * Onboarding.
 */
@Composable
fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> currentOnResult(granted) }

    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    currentOnResult(true)
                } else {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                currentOnResult(true)
            }
        }
    }
}
