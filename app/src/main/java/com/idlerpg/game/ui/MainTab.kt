package com.idlerpg.game.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import com.idlerpg.game.R

enum class MainTab(@StringRes val label: Int, val icon: ImageVector) {
    CHARACTER(R.string.tab_character, Icons.Filled.Person),
    EQUIPMENT(R.string.tab_equipment, Icons.Filled.Shield),
    SHOP(R.string.tab_shop, Icons.Filled.Storefront),
    OPTIONS(R.string.tab_options, Icons.Filled.Settings),
}
