package com.sanmer.mrepo.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.sanmer.mrepo.BuildConfig
import com.sanmer.mrepo.R
import com.sanmer.mrepo.app.Logcat
import com.sanmer.mrepo.datastore.WorkingMode
import com.sanmer.mrepo.ui.component.SettingNormalItem
import com.sanmer.mrepo.ui.component.TopAppBarTitle
import com.sanmer.mrepo.ui.navigation.graphs.SettingsScreen
import com.sanmer.mrepo.ui.providable.LocalUserPreferences
import com.sanmer.mrepo.ui.screens.settings.items.NonRootItem
import com.sanmer.mrepo.ui.screens.settings.items.RootItem
import com.sanmer.mrepo.ui.utils.navigateSingleTopTo
import com.sanmer.mrepo.ui.utils.none
import com.sanmer.mrepo.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val userPreferences = LocalUserPreferences.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            Logcat.writeTo(context, uri)
                .onSuccess {
                    val message = context.getString(R.string.install_logs_saved)
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short
                    )
                }.onFailure {
                    val message = context.getString(
                        R.string.install_logs_save_failed,
                        it.message ?: context.getString(R.string.unknown_error)
                    )
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short
                    )
                }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopBar(
                exportLog = { launcher.launch(Logcat.logfile) },
                isRoot = userPreferences.isRoot,
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.none
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when {
                userPreferences.isRoot -> RootItem(
                    isAlive = viewModel.isProviderAlive,
                    version = viewModel.version
                )
                userPreferences.isNonRoot -> NonRootItem()
            }

            SettingNormalItem(
                icon = R.drawable.launcher_outline,
                title = stringResource(id = R.string.settings_app),
                desc = stringResource(id = R.string.settings_app_desc),
                onClick = {
                    navController.navigateSingleTopTo(SettingsScreen.App.route)
                }
            )

            SettingNormalItem(
                icon = R.drawable.git_pull_request,
                title = stringResource(id = R.string.settings_repo),
                desc = stringResource(id = R.string.settings_repo_desc),
                onClick = {
                    navController.navigateSingleTopTo(SettingsScreen.Repositories.route)
                }
            )

            SettingNormalItem(
                icon = R.drawable.components,
                title = stringResource(id = R.string.setup_mode),
                desc = stringResource(id = when (userPreferences.workingMode) {
                    WorkingMode.MODE_ROOT -> R.string.setup_root_title
                    WorkingMode.MODE_SHIZUKU -> R.string.setup_shizuku_title
                    WorkingMode.MODE_NON_ROOT -> R.string.setup_non_root_title
                    else -> R.string.settings_root_none
                }),
                onClick = {
                    navController.navigateSingleTopTo(SettingsScreen.WorkingMode.route)
                }
            )

            SettingNormalItem(
                icon = R.drawable.award,
                title = stringResource(id = R.string.settings_about),
                desc = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = {
                    navController.navigateSingleTopTo(SettingsScreen.About.route)
                }
            )
        }
    }
}

@Composable
private fun TopBar(
    exportLog: () -> Unit,
    isRoot: Boolean,
    scrollBehavior: TopAppBarScrollBehavior
) = TopAppBar(
    title = {
        TopAppBarTitle(text = stringResource(id = R.string.page_settings))
    },
    actions = {
        IconButton(
            onClick = exportLog
        ) {
            Icon(
                painter = painterResource(id = R.drawable.bug),
                contentDescription = null
            )
        }

        var expanded by remember { mutableStateOf(false) }
        IconButton(
            onClick = { expanded = true },
            enabled = isRoot
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reload),
                contentDescription = null
            )

            SettingsMenu(
                expanded = expanded,
                onClose = { expanded = false }
            )
        }
    },
    scrollBehavior = scrollBehavior
)