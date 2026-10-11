package org.lumina.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lumina.player.R
import org.lumina.player.data.preferences.AppThemeMode
import org.lumina.player.data.preferences.DarkThemeStyle
import org.lumina.player.data.preferences.ThemeSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    themeSettings: ThemeSettings,
    filterShortAudio: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetDarkThemeStyle: (DarkThemeStyle) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetFilterShortAudio: (Boolean) -> Unit,
    onRescanLibrary: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 主题模式选择
            Text(
                text = stringResource(R.string.theme_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = themeSettings.themeMode == AppThemeMode.SYSTEM,
                    onClick = { onSetThemeMode(AppThemeMode.SYSTEM) },
                    label = { Text(stringResource(R.string.theme_system)) }
                )
                FilterChip(
                    selected = themeSettings.themeMode == AppThemeMode.LIGHT,
                    onClick = { onSetThemeMode(AppThemeMode.LIGHT) },
                    label = { Text(stringResource(R.string.theme_light)) }
                )
                FilterChip(
                    selected = themeSettings.themeMode == AppThemeMode.DARK,
                    onClick = { onSetThemeMode(AppThemeMode.DARK) },
                    label = { Text(stringResource(R.string.theme_dark)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 深色风格
            Text(
                text = stringResource(R.string.dark_style),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = themeSettings.darkThemeStyle == DarkThemeStyle.SLATE,
                    onClick = { onSetDarkThemeStyle(DarkThemeStyle.SLATE) },
                    label = { Text(stringResource(R.string.dark_slate)) }
                )
                FilterChip(
                    selected = themeSettings.darkThemeStyle == DarkThemeStyle.AMOLED,
                    onClick = { onSetDarkThemeStyle(DarkThemeStyle.AMOLED) },
                    label = { Text(stringResource(R.string.dark_amoled)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 动态壁纸取色
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = stringResource(R.string.dynamic_color), style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = themeSettings.dynamicColor,
                    onCheckedChange = onSetDynamicColor
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 过滤短音频
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.filter_short_audio), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = filterShortAudio,
                    onCheckedChange = onSetFilterShortAudio
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 重新扫描曲库
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onRescanLibrary()
                        onDismiss()
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.rescan_library),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
