package org.lumina.player.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.lumina.player.R
import org.lumina.player.core.dsp.AudioDspController
import kotlin.math.abs

@Composable
fun EqualizerDialog(
    dspController: AudioDspController?,
    playbackSpeed: Float = 1.0f,
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    var isEnabled by remember { mutableStateOf(dspController?.isEnabled() ?: true) }
    var bassBoost by remember { mutableFloatStateOf((dspController?.getBassBoost()?.toFloat() ?: 0f) / 10f) }
    var virtualizer by remember { mutableFloatStateOf((dspController?.getVirtualizer()?.toFloat() ?: 0f) / 10f) }
    var speed by remember { mutableFloatStateOf(playbackSpeed) }

    val numBands = dspController?.getNumberOfBands() ?: 5
    val bandLevels = remember(numBands) {
        (0 until numBands).map { band ->
            mutableFloatStateOf(dspController?.getBandLevel(band.toShort())?.toFloat() ?: 0f)
        }
    }

    val presets = remember { org.lumina.player.core.dsp.EqPresets.ALL }

    // Determine initial matching preset based on current band levels
    var selectedPresetResId by remember {
        val currentLevels = bandLevels.map { it.floatValue.toInt().toShort() }
        val initialMatch = org.lumina.player.core.dsp.EqPresets.findMatchingPreset(currentLevels)?.nameResId
        mutableStateOf<Int?>(initialMatch)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.equalizer),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        dspController?.setEnabled(it)
                    }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Equalizer preset selector
                Text(
                    text = stringResource(R.string.eq_presets),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = selectedPresetResId == preset.nameResId
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isEnabled && dspController != null) {
                                    selectedPresetResId = preset.nameResId
                                    preset.bandLevels.forEachIndexed { idx, lvl ->
                                        if (idx < bandLevels.size) {
                                            bandLevels[idx].floatValue = lvl.toFloat()
                                            dspController.setBandLevel(idx.toShort(), lvl)
                                        }
                                    }
                                }
                            },
                            label = { Text(stringResource(preset.nameResId), fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Graphic equalizer frequency band sliders
                Text(
                    text = "Graphic EQ Bands",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                (0 until numBands).forEach { band ->
                    val centerFreqHz = dspController?.getBandCenterFreq(band.toShort()) ?: 1000
                    val freqLabel = if (centerFreqHz >= 1000) "${centerFreqHz / 1000} kHz" else "$centerFreqHz Hz"
                    val levelState = bandLevels[band]

                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = freqLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${(levelState.floatValue / 100).toInt()} dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = levelState.floatValue,
                            onValueChange = { newValue ->
                                selectedPresetResId = null
                                levelState.floatValue = newValue
                                dspController?.setBandLevel(band.toShort(), newValue.toInt().toShort())
                            },
                            valueRange = -1500f..1500f,
                            enabled = isEnabled
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Bass Boost effect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.bass_boost), style = MaterialTheme.typography.bodyLarge)
                    Text(text = "${bassBoost.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = bassBoost,
                    onValueChange = {
                        bassBoost = it
                        dspController?.setBassBoost((it * 10).toInt().toShort())
                    },
                    valueRange = 0f..100f,
                    enabled = isEnabled
                )

                // 3D Virtualizer surround effect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.virtualizer), style = MaterialTheme.typography.bodyLarge)
                    Text(text = "${virtualizer.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = virtualizer,
                    onValueChange = {
                        virtualizer = it
                        dspController?.setVirtualizer((it * 10).toInt().toShort())
                    },
                    valueRange = 0f..100f,
                    enabled = isEnabled
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Playback speed adjustment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.playback_speed), style = MaterialTheme.typography.bodyLarge)
                    Text(text = String.format("%.2fx", speed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = speed,
                    onValueChange = {
                        speed = it
                        onSetPlaybackSpeed(it)
                    },
                    valueRange = 0.5f..2.0f,
                    steps = 5
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.save))
            }
        }
    )
}
