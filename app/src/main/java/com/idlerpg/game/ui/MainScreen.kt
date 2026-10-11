package com.idlerpg.game.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.idlerpg.game.R

/**
 * Root screen: adventure view on top, menu bar at the bottom.
 * Tapping a tab opens its panel; tapping the active tab again returns to the adventure view.
 */
@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableStateOf<MainTab?>(null) }

    Scaffold(
        bottomBar = {
            MainMenuBar(
                selected = selectedTab,
                onSelect = { tab -> selectedTab = if (selectedTab == tab) null else tab },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AdventureView()
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

@Composable
private fun AdventureView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.adventure_placeholder), style = MaterialTheme.typography.headlineSmall)
    }
}

/** Routes each tab to its feature screen. Each branch is owned by that tab's agent (.claude/agents). */
@Composable
private fun TabPanel(tab: MainTab) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (tab) {
            MainTab.CHARACTER -> PlaceholderPanel(tab)
            MainTab.EQUIPMENT -> PlaceholderPanel(tab)
            MainTab.SHOP -> PlaceholderPanel(tab)
            MainTab.OPTIONS -> com.idlerpg.game.ui.options.OptionsScreen()
        }
    }
}

@Composable
private fun PlaceholderPanel(tab: MainTab) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.panel_placeholder, stringResource(tab.label)),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
