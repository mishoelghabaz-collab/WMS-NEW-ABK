package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.ThemeMode

@Immutable
data class WmsExtendedColors(
    val isDark: Boolean,
    val surfaceElevated: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val receiveGreen: Color,
    val onReceiveGreen: Color,
    val receiveGreenContainer: Color,
    val issueRed: Color,
    val onIssueRed: Color,
    val issueRedContainer: Color,
    val transferAmber: Color,
    val onTransferAmber: Color,
    val transferAmberContainer: Color,
    val statusInStock: Color,
    val statusLowStock: Color,
    val statusOutOfStock: Color
)

private val DarkExtendedColors = WmsExtendedColors(
    isDark = true,
    surfaceElevated = WmsSurfaceElevated,
    surfaceVariant = WmsSurfaceVariant,
    outline = WmsOutline,
    receiveGreen = ReceiveGreen,
    onReceiveGreen = OnReceiveGreen,
    receiveGreenContainer = ReceiveGreenContainer,
    issueRed = IssueRed,
    onIssueRed = OnIssueRed,
    issueRedContainer = IssueRedContainer,
    transferAmber = TransferAmber,
    onTransferAmber = OnTransferAmber,
    transferAmberContainer = TransferAmberContainer,
    statusInStock = ReceiveGreen,
    statusLowStock = TransferAmber,
    statusOutOfStock = IssueRed
)

private val LightExtendedColors = WmsExtendedColors(
    isDark = false,
    surfaceElevated = WmsLightSurfaceElevated,
    surfaceVariant = WmsLightSurfaceVariant,
    outline = WmsLightOutline,
    receiveGreen = LightReceiveGreen,
    onReceiveGreen = LightOnReceiveGreen,
    receiveGreenContainer = LightReceiveGreenContainer,
    issueRed = LightIssueRed,
    onIssueRed = LightOnIssueRed,
    issueRedContainer = LightIssueRedContainer,
    transferAmber = LightTransferAmber,
    onTransferAmber = LightOnTransferAmber,
    transferAmberContainer = LightTransferAmberContainer,
    statusInStock = LightReceiveGreen,
    statusLowStock = LightTransferAmber,
    statusOutOfStock = LightIssueRed
)

val LocalWmsExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

val MaterialTheme.wmsColors: WmsExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalWmsExtendedColors.current

private val WmsDarkColorScheme = darkColorScheme(
    primary = WmsPrimary,
    onPrimary = WmsOnPrimary,
    primaryContainer = WmsPrimaryContainer,
    onPrimaryContainer = WmsOnPrimaryContainer,
    secondary = TransferAmber,
    onSecondary = OnTransferAmber,
    secondaryContainer = TransferAmberContainer,
    onSecondaryContainer = TransferAmber,
    tertiary = ReceiveGreen,
    onTertiary = OnReceiveGreen,
    tertiaryContainer = ReceiveGreenContainer,
    onTertiaryContainer = ReceiveGreen,
    error = IssueRed,
    onError = OnIssueRed,
    errorContainer = IssueRedContainer,
    onErrorContainer = IssueRed,
    background = WmsBackground,
    onBackground = WmsOnBackground,
    surface = WmsSurface,
    onSurface = WmsOnSurface,
    surfaceVariant = WmsSurfaceVariant,
    onSurfaceVariant = WmsOnSurfaceMuted,
    outline = WmsOutline
)

private val WmsLightColorScheme = lightColorScheme(
    primary = WmsLightPrimary,
    onPrimary = WmsLightOnPrimary,
    primaryContainer = WmsLightPrimaryContainer,
    onPrimaryContainer = WmsLightOnPrimaryContainer,
    secondary = LightTransferAmber,
    onSecondary = LightOnTransferAmber,
    secondaryContainer = LightTransferAmberContainer,
    onSecondaryContainer = LightTransferAmber,
    tertiary = LightReceiveGreen,
    onTertiary = LightOnReceiveGreen,
    tertiaryContainer = LightReceiveGreenContainer,
    onTertiaryContainer = LightReceiveGreen,
    error = LightIssueRed,
    onError = LightOnIssueRed,
    errorContainer = LightIssueRedContainer,
    onErrorContainer = LightIssueRed,
    background = WmsLightBackground,
    onBackground = WmsLightOnBackground,
    surface = WmsLightSurface,
    onSurface = WmsLightOnSurface,
    surfaceVariant = WmsLightSurfaceVariant,
    onSurfaceVariant = WmsLightOnSurfaceMuted,
    outline = WmsLightOutline
)

@Composable
fun PlantWmsTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) WmsDarkColorScheme else WmsLightColorScheme
    val extendedColors = if (isDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalWmsExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PlantWmsTheme(
        themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        content = content
    )
}
