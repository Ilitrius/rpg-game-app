package com.idlerpg.game.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idlerpg.game.R
import com.idlerpg.game.ui.adventure.AdventureScreen
import com.idlerpg.game.update.InAppUpdater
import com.idlerpg.game.update.UpdateUiState
import com.idlerpg.game.ui.character.CharacterScreen
import com.idlerpg.game.ui.equipment.EquipmentScreen
import com.idlerpg.game.ui.options.OptionsScreen
import com.idlerpg.game.ui.shop.ShopScreen

/**
 * Root screen: adventure view on top, menu bar at the bottom.
 * Tapping a tab opens its panel; tapping the active tab again returns to the adventure view.
 */
@Composable
fun MainScreen(updater: InAppUpdater? = null) {
    var selectedTab by rememberSaveable { mutableStateOf<MainTab?>(null) }

    val snackbar = remember { SnackbarHostState() }
    if (updater != null) UpdatePrompt(updater, snackbar)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            MainMenuBar(
                selected = selectedTab,
                onSelect = { tab -> selectedTab = if (selectedTab == tab) null else tab },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Owned by the Spielwelt agent (ui/adventure).
            AdventureScreen()
            selectedTab?.let { TabPanel(it) }
        }
    }
}

@Composable
private fun MainMenuBar(selected: MainTab?, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            val label = stringResource(tab.label)
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = label) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

/** Routes each tab to its feature screen. Each branch is owned by that tab's agent (.claude/agents). */
@Composable
private fun TabPanel(tab: MainTab) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (tab) {
            MainTab.CHARACTER -> CharacterScreen()
            MainTab.EQUIPMENT -> EquipmentScreen()
            MainTab.SHOP -> ShopScreen()
            MainTab.OPTIONS -> OptionsScreen()
        }
    }
}

/** Shows the Play in-app update hint ("Update" / "Restart") as an indefinite snackbar. */
@Composable
private fun UpdatePrompt(updater: InAppUpdater, snackbar: SnackbarHostState) {
    val state by updater.state.collectAsStateWithLifecycle()
    val message = when (state) {
        UpdateUiState.AVAILABLE -> stringResource(R.string.update_available)
        UpdateUiState.READY -> stringResource(R.string.update_ready)
        else -> null
    }
    val action = when (state) {
        UpdateUiState.AVAILABLE -> stringResource(R.string.update_action)
        UpdateUiState.READY -> stringResource(R.string.update_restart)
        else -> null
    }
    LaunchedEffect(state, message, action) {
        if (message == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = message,
            actionLabel = action,
            withDismissAction = state == UpdateUiState.AVAILABLE,
            duration = SnackbarDuration.Indefinite,
        )
        when {
            result == SnackbarResult.ActionPerformed && state == UpdateUiState.AVAILABLE -> updater.startUpdate()
            result == SnackbarResult.ActionPerformed && state == UpdateUiState.READY -> updater.completeUpdate()
            result == SnackbarResult.Dismissed && state == UpdateUiState.AVAILABLE -> updater.dismiss()
        }
    }
}
