package org.lumina.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.lumina.player.R
import org.lumina.player.core.lyrics.LyricLine
import org.lumina.player.core.lyrics.LrcParser

/**
 * 实时同步滚动歌词组件 (支持逐行高亮、点击定位跳转、时间微调)
 */
@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentPositionMs: Long,
    offsetMs: Long,
    onSeekTo: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_lyrics),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        return
    }

    val activeIndex = LrcParser.findCurrentIndex(lyrics, currentPositionMs, offsetMs)
    val listState = rememberLazyListState()

    // 随进度自动平滑滚动到当前活跃行居中
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex in lyrics.indices) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部微调控制条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { onAdjustOffset(-500L) },
                label = { Text(stringResource(R.string.lyrics_delay_500ms), fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent)
            )
            if (offsetMs != 0L) {
                Text(
                    text = "${if (offsetMs > 0) "+" else ""}${offsetMs / 1000.0}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                AssistChip(
                    onClick = onResetOffset,
                    label = { Text(stringResource(R.string.lyrics_reset), fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent)
                )
            }
            AssistChip(
                onClick = { onAdjustOffset(500L) },
                label = { Text(stringResource(R.string.lyrics_advance_500ms), fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeIndex
                val textColor by animateColorAsState(
                    targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    animationSpec = tween(300),
                    label = "textColor"
                )

                Surface(
                    color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { onSeekTo(line.startMs) }
                ) {
                    Text(
                        text = line.content,
                        style = if (isActive) {
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                lineHeight = 28.sp
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                            )
                        },
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                            .alpha(if (isActive) 1.0f else 0.45f)
                    )
                }
            }
        }
    }
}
