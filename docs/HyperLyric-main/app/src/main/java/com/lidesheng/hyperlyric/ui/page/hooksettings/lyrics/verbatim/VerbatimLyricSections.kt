package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.verbatim

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.verbatimLyricSections(
    gradientStyle: Boolean,
    onGradientStyleChange: (Boolean) -> Unit,
    lineDisplay: Boolean,
    onLineDisplayChange: (Boolean) -> Unit,
    syllableRelative: Boolean,
    onSyllableRelativeChange: (Boolean) -> Unit,
    syllableHighlight: Boolean,
    onSyllableHighlightChange: (Boolean) -> Unit,
    wordMotionEnabled: Boolean,
    onWordMotionEnabledChange: (Boolean) -> Unit,
    wordMotionLatinByCharacter: Boolean,
    onWordMotionLatinByCharacterChange: (Boolean) -> Unit,
    wordMotionCjkLift: Float,
    onWordMotionCjkLiftClick: () -> Unit,
    wordMotionCjkWave: Float,
    onWordMotionCjkWaveClick: () -> Unit,
    wordMotionLatinLift: Float,
    onWordMotionLatinLiftClick: () -> Unit,
    wordMotionLatinWave: Float,
    onWordMotionLatinWaveClick: () -> Unit
) {
    item(key = "verbatim_lyric") {
        Column {
            SmallTitle(text = stringResource(id = R.string.title_verbatim_lyric))
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    SwitchPreference(
                        title = stringResource(id = R.string.title_syllable_line_display),
                        summary = stringResource(id = R.string.summary_syllable_line_display),
                        checked = lineDisplay,
                        onCheckedChange = onLineDisplayChange
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.title_syllable_relative),
                        summary = stringResource(id = R.string.summary_syllable_relative),
                        checked = syllableRelative,
                        onCheckedChange = onSyllableRelativeChange
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.title_syllable_highlight),
                        enabled = syllableRelative,
                        checked = syllableHighlight,
                        onCheckedChange = onSyllableHighlightChange
                    )
                }
            }
            SmallTitle(text = stringResource(id = R.string.title_lyric_effects))
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    SwitchPreference(
                        title = stringResource(id = R.string.title_gradient_progress),
                        checked = gradientStyle,
                        onCheckedChange = onGradientStyleChange
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.title_word_motion),
                        checked = wordMotionEnabled,
                        onCheckedChange = onWordMotionEnabledChange
                    )
                    AnimatedVisibility(visible = wordMotionEnabled) {
                        Column {
                            SwitchPreference(
                                title = stringResource(
                                    id = R.string.title_word_motion_latin_by_character
                                ),
                                summary = stringResource(
                                    id = R.string.summary_word_motion_latin_by_character
                                ),
                                checked = wordMotionLatinByCharacter,
                                onCheckedChange = onWordMotionLatinByCharacterChange
                            )
                            ArrowPreference(
                                title = stringResource(id = R.string.title_word_motion_cjk_lift),
                                onClick = onWordMotionCjkLiftClick,
                                endActions = {
                                    Text(
                                        String.format("%.2f", wordMotionCjkLift),
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                }
                            )
                            ArrowPreference(
                                title = stringResource(id = R.string.title_word_motion_cjk_wave),
                                endActions = {
                                    Text(
                                        String.format("%.2f", wordMotionCjkWave),
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = onWordMotionCjkWaveClick
                            )
                            ArrowPreference(
                                title = stringResource(id = R.string.title_word_motion_latin_lift),
                                endActions = {
                                    Text(
                                        String.format("%.2f", wordMotionLatinLift),
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = onWordMotionLatinLiftClick
                            )
                            ArrowPreference(
                                title = stringResource(id = R.string.title_word_motion_latin_wave),
                                endActions = {
                                    Text(
                                        String.format("%.2f", wordMotionLatinWave),
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = onWordMotionLatinWaveClick
                            )
                        }
                    }
                }
            }
        }
    }
}
