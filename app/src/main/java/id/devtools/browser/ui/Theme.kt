package id.devtools.browser.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PurplePrimary = Color(0xFF7C5CFF)
private val PurpleContainer = Color(0xFF5A3FD4)
private val Background = Color(0xFF0D0D12)
private val Surface = Color(0xFF16161D)
private val SurfaceVariant = Color(0xFF1E1E28)
private val OnBackground = Color(0xFFE8E8F0)
private val TealColor = Color(0xFF1FBFA8)
private val BlueBtnColor = Color(0xFF2E7CF6)
private val OrangeBtnColor = Color(0xFFE8A33D)
private val RedBtnColor = Color(0xFFD95F4B)

private val DarkScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = Color.White,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnBackground,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = Color(0xFFB8B8C8),
    secondary = TealColor,
    tertiary = BlueBtnColor,
)

private val LightScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
)

@Composable
fun DevToolsBrowserTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        content = content,
    )
}

/** Accent colors matching the reference screenshot buttons. */
object DevToolsColors {
    val Teal: Color = TealColor
    val Blue: Color = BlueBtnColor
    val Orange: Color = OrangeBtnColor
    val Red: Color = RedBtnColor
}
