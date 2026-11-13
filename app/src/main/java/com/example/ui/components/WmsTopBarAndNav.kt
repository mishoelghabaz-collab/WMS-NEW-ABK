package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.Factory
import com.example.data.model.ThemeMode
import com.example.data.model.UserRole
import com.example.ui.navigation.AdminRoute
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.InventoryRoute
import com.example.ui.navigation.MapRoute
import com.example.ui.navigation.WmsRoute
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors

data class BottomNavDestination(
    val route: WmsRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val wmsBottomNavDestinations = listOf(
    BottomNavDestination(
        route = DashboardRoute,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_dashboard"
    ),
    BottomNavDestination(
        route = InventoryRoute,
        labelRes = R.string.nav_inventory,
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2,
        testTag = "nav_inventory"
    ),
    BottomNavDestination(
        route = HistoryRoute,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    ),
    BottomNavDestination(
        route = MapRoute,
        labelRes = R.string.nav_map,
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
        testTag = "nav_map"
    ),
    BottomNavDestination(
        route = AdminRoute,
        labelRes = R.string.nav_admin,
        selectedIcon = Icons.Filled.AdminPanelSettings,
        unselectedIcon = Icons.Outlined.AdminPanelSettings,
        testTag = "nav_admin"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WmsTopAppBar(
    factories: List<Factory>,
    selectedFactoryId: String,
    language: AppLanguage,
    themeMode: ThemeMode,
    userRole: UserRole,
    onSelectFactory: (String) -> Unit,
    onToggleLanguage: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onOpenScanner: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wmsColors = MaterialTheme.wmsColors
    var plantMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Plant selector dropdown pill
                Box {
                    Surface(
                        onClick = { if (factories.isNotEmpty()) plantMenuExpanded = true },
                        color = wmsColors.surfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, wmsColors.outline),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_plant_selector")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Factory,
                                contentDescription = stringResource(R.string.cd_plant_selector),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedFactoryId.ifBlank {
                                    stringResource(R.string.no_plant_assigned)
                                },
                                style = WmsMonoTypography.partNumber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = plantMenuExpanded,
                        onDismissRequest = { plantMenuExpanded = false }
                    ) {
                        factories.forEach { factory ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${factory.code} · ${factory.name}",
                                        style = WmsMonoTypography.location
                                    )
                                },
                                onClick = {
                                    onSelectFactory(factory.code)
                                    plantMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // User Role Badge
                RoleBadge(role = userRole)
            }
        },
        actions = {
            // Theme Mode Switcher (Dark / Light / System)
            Box {
                IconButton(
                    onClick = { themeMenuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("topbar_theme_toggle")
                ) {
                    Icon(
                        imageVector = when (themeMode) {
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                        },
                        contentDescription = stringResource(R.string.cd_theme_toggle),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = themeMenuExpanded,
                    onDismissRequest = { themeMenuExpanded = false }
                ) {
                    val options = listOf(
                        Triple(ThemeMode.DARK, R.string.theme_dark, Icons.Filled.DarkMode),
                        Triple(ThemeMode.LIGHT, R.string.theme_light, Icons.Filled.LightMode),
                        Triple(ThemeMode.SYSTEM, R.string.theme_system, Icons.Filled.BrightnessAuto)
                    )
                    options.forEach { (mode, labelRes, icon) ->
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (themeMode == mode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            },
                            trailingIcon = {
                                if (themeMode == mode) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            text = { Text(stringResource(labelRes)) },
                            onClick = {
                                onSelectThemeMode(mode)
                                themeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Language Toggle (EN / AR)
            Surface(
                onClick = onToggleLanguage,
                color = wmsColors.surfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, wmsColors.outline),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("topbar_language_toggle")
            ) {
                Text(
                    text = if (language == AppLanguage.EN) {
                        stringResource(R.string.lang_en)
                    } else {
                        stringResource(R.string.lang_ar)
                    },
                    style = WmsMonoTypography.location,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

            // Barcode Scanner icon button
            IconButton(
                onClick = onOpenScanner,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("topbar_scanner_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = stringResource(R.string.cd_barcode_scanner),
                    tint = wmsColors.receiveGreen
                )
            }

            // Sign Out icon button
            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("topbar_sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.cd_sign_out),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
fun RoleBadge(
    role: UserRole,
    modifier: Modifier = Modifier
) {
    val wmsColors = MaterialTheme.wmsColors
    val (bgColor, fgColor, labelRes) = when (role) {
        UserRole.ADMIN -> Triple(wmsColors.receiveGreenContainer, wmsColors.receiveGreen, R.string.role_admin)
        UserRole.SUPERVISOR -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            R.string.role_supervisor
        )
        UserRole.OPERATOR -> Triple(wmsColors.transferAmberContainer, wmsColors.transferAmber, R.string.role_operator)
        UserRole.VIEWER -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.string.role_viewer
        )
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.45f)),
        modifier = modifier.testTag("topbar_role_badge")
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fgColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun WmsBottomNavigationBar(
    currentRoute: WmsRoute,
    onNavigate: (WmsRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets.navigationBars
    ) {
        wmsBottomNavDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(destination.testTag)
            )
        }
    }
}

@Composable
fun WmsNavigationRail(
    currentRoute: WmsRoute,
    onNavigate: (WmsRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        wmsBottomNavDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            val label = stringResource(destination.labelRes)
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(destination.testTag)
            )
        }
    }
}
