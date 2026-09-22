package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.lyricDisplaySections(
    textSize: Int,
    onTextSizeClick: () -> Unit,
    textSizeRatio: Float,
    onTextSizeRatioClick: () -> Unit,
    fadingEdge: Int,
    onFadingEdgeClick: () -> Unit,
    textColorStyle: Int,
    onTextColorStyleChange: (Int) -> Unit,
    customFontPath: String,
    onFontPathClick: () -> Unit,
    fontWeight: Int,
    onFontWeightClick: () -> Unit,
    fontItalic: Boolean,
    onFontItalicChange: (Boolean) -> Unit,
    narrowLatinFont: Boolean,
    onNarrowLatinFontChange: (Boolean) -> Unit
) {
    item(key = "basic_style_title") {
        SmallTitle(text = stringResource(id = R.string.title_basic_style))
    }
    item(key = "basic_style_content") {
        val textColorOptions = listOf(
            stringResource(id = R.string.option_text_color_default),
            stringResource(id = R.string.option_text_color_cover_color),
            stringResource(id = R.string.option_text_color_cover_gradient),
            stringResource(id = R.string.option_text_color_follow_status_bar)
        )
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                ArrowPreference(
                    title = stringResource(id = R.string.title_size),
                    endActions = {
                        Text(
                            "$textSize",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = onTextSizeClick
                )
                ArrowPreference(
                    title = stringResource(id = R.string.title_text_size_ratio),
                    endActions = {
                        Text(
                            stringResource(
                                id = R.string.format_percent,
                                (textSizeRatio * 100).toInt()
                            ),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = onTextSizeRatioClick
                )
                ArrowPreference(
                    title = stringResource(id = R.string.title_fading_edge),
                    endActions = {
                        Text(
                            "$fadingEdge",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = onFadingEdgeClick
                )
                OverlayDropdownPreference(
                    title = stringResource(id = R.string.title_text_color),
                    items = textColorOptions,
                    selectedIndex = textColorStyle.coerceIn(
                        RootConstants.TEXT_COLOR_STYLE_DEFAULT,
                        RootConstants.TEXT_COLOR_STYLE_FOLLOW_STATUS_BAR
                    ),
                    onSelectedIndexChange = onTextColorStyleChange
                )
            }
        }
    }

    item(key = "font_style_title") {
        SmallTitle(text = stringResource(id = R.string.title_font_style))
    }
    item(key = "font_style_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                ArrowPreference(
                    title = stringResource(id = R.string.title_custom_font),
                    endActions = {
                        Text(
                            customFontPath.ifEmpty { stringResource(id = R.string.summary_default_font) },
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = onFontPathClick
                )
                SwitchPreference(
                    title = stringResource(id = R.string.title_narrow_latin_font),
                    checked = narrowLatinFont,
                    onCheckedChange = onNarrowLatinFontChange
                )
                ArrowPreference(
                    title = stringResource(id = R.string.title_font_weight),
                    endActions = {
                        Text(
                            fontWeight.toString(),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = onFontWeightClick
                )
                SwitchPreference(
                    title = stringResource(id = R.string.title_italic),
                    checked = fontItalic,
                    onCheckedChange = onFontItalicChange
                )
            }
        }
    }
}
