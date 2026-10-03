package com.sykeptical.hyperpop.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.ui.AppCategory
import com.sykeptical.hyperpop.ui.SortOption
import com.sykeptical.hyperpop.ui.system.HyperPopType

@Composable
fun AppListFilterSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: AppCategory,
    onCategoryChange: (AppCategory) -> Unit,
    sortOption: SortOption,
    onSortChange: (SortOption) -> Unit,
    showSystemCategory: Boolean = false,
    systemSelected: Boolean = false,
    onSystemSelected: (Boolean) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                singleLine = true,
                textStyle = HyperPopType.body.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = HyperPopType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterLabel(
                text = if (sortOption == SortOption.NAME_AZ) "A–Z" else "Z–A",
                selected = true,
                onClick = {
                    onSortChange(if (sortOption == SortOption.NAME_AZ) SortOption.NAME_ZA else SortOption.NAME_AZ)
                },
            )
            if (showSystemCategory) {
                FilterLabel(
                    text = stringResource(R.string.system_integrations),
                    selected = systemSelected,
                    onClick = { onSystemSelected(true) },
                )
            }
            AppCategory.entries.forEach { category ->
                val selected = !systemSelected && selectedCategory == category
                FilterLabel(
                    text = stringResource(categoryLabel(category)),
                    selected = selected,
                    onClick = { onCategoryChange(category) },
                )
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = HyperPopType.secondary,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@StringRes
private fun categoryLabel(category: AppCategory): Int = when (category) {
    AppCategory.ALL -> R.string.cat_all
    AppCategory.MUSIC -> R.string.cat_music
    AppCategory.MAPS -> R.string.cat_nav
    AppCategory.TIMER -> R.string.cat_timer
    AppCategory.OTHER -> R.string.cat_other
}
