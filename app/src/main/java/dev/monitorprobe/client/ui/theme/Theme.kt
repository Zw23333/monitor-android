package dev.monitorprobe.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app's theme. On Android 12 and later the whole palette is derived from the
 * user's wallpaper by the platform, which is what "原生自动取色" means here: no
 * brand colours are imposed and every surface, container and accent follows the
 * system. Below 12 the platform has no such palette, so a plain baseline scheme
 * stands in — the app still works, it simply cannot follow the wallpaper.
 *
 * [dynamicColor] is a parameter so a caller can force the baseline palette, which
 * is useful in a preview and is the only way to see it on a modern device.
 */
@Composable
fun MonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
