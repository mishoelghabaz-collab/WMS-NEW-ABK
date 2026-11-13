package com.example

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AppLanguage
import com.example.data.model.ThemeMode
import com.example.data.preferences.WmsPreferencesRepository
import com.example.data.repository.WmsRepository
import com.example.ui.auth.LocalHostActivity
import com.example.ui.auth.SignInScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.navigation.WmsAppShell
import com.example.ui.theme.PlantWmsTheme
import com.example.ui.viewmodel.WmsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferencesRepository = WmsPreferencesRepository(applicationContext)

        setContent {
            val language by preferencesRepository.appLanguage
                .collectAsStateWithLifecycle(initialValue = AppLanguage.EN)
            val themeMode by preferencesRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            val scope = rememberCoroutineScope()

            val isDarkEffective = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(isDarkEffective) {
                val barStyle = if (isDarkEffective) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            LocalizedWmsContainer(language = language) {
                CompositionLocalProvider(LocalHostActivity provides this@MainActivity) {
                    PlantWmsTheme(themeMode = themeMode) {
                        WmsRootContent(
                            language = language,
                            themeMode = themeMode,
                            onToggleLanguage = {
                                scope.launch {
                                    val next = if (language == AppLanguage.EN) {
                                        AppLanguage.AR
                                    } else {
                                        AppLanguage.EN
                                    }
                                    preferencesRepository.setAppLanguage(next)
                                }
                            },
                            onSelectThemeMode = { nextMode ->
                                scope.launch {
                                    preferencesRepository.setThemeMode(nextMode)
                                }
                            },
                            preferencesRepository = preferencesRepository
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalizedWmsContainer(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    val localizedContext = remember(language, baseContext) {
        val locale = Locale.forLanguageTag(language.code)
        val config = Configuration(baseContext.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val localeResources = baseContext.createConfigurationContext(config).resources
        object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = localeResources
            override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
                baseContext.createConfigurationContext(overrideConfiguration)
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalLayoutDirection provides layoutDirection,
        content = content
    )
}

@Composable
private fun WmsRootContent(
    language: AppLanguage,
    themeMode: ThemeMode,
    onToggleLanguage: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    preferencesRepository: WmsPreferencesRepository
) {
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        SignInScreen(
            language = language,
            themeMode = themeMode,
            onToggleLanguage = onToggleLanguage,
            onSelectThemeMode = onSelectThemeMode,
            onAuthSuccess = {}
        )
    } else {
        key(user.uid) {
            AuthenticatedWmsSession(
                user = user,
                preferencesRepository = preferencesRepository
            )
        }
    }
}

@Composable
private fun AuthenticatedWmsSession(
    user: FirebaseUser,
    preferencesRepository: WmsPreferencesRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: WmsViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                WmsViewModel(
                    repository = WmsRepository(db = db, auth = Firebase.auth),
                    preferences = preferencesRepository,
                    currentUserId = user.uid,
                    currentUserName = user.displayName.orEmpty(),
                    currentUserEmail = user.email.orEmpty(),
                    isEmailVerified = user.isEmailVerified
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WmsAppShell(
        uiState = uiState,
        viewModel = viewModel,
        onSignOut = {
            signOut(
                credentialManager = credentialManager,
                scope = scope
            )
        }
    )
}
