package org.lumina.player.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.widget.RemoteViews
import org.lumina.player.LuminaApp
import org.lumina.player.MainActivity
import org.lumina.player.R
import org.lumina.player.core.model.Song

class LuminaPlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = runCatching { LuminaApp.instance }.getOrNull()
        val song = app?.playerController?.currentSong?.value
        val isPlaying = app?.playerController?.isPlaying?.value ?: false
        val artwork = app?.playerController?.currentArtwork?.value

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, song, isPlaying, artwork)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = runCatching { LuminaApp.instance }.getOrNull() ?: return

        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                app.playerController.togglePlayPause()
            }
            ACTION_PREV -> {
                app.playerController.skipPrevious()
            }
            ACTION_NEXT -> {
                app.playerController.skipNext()
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "org.lumina.player.widget.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "org.lumina.player.widget.ACTION_PREV"
        const val ACTION_NEXT = "org.lumina.player.widget.ACTION_NEXT"

        fun updateAll(context: Context, song: Song?, isPlaying: Boolean, artwork: Bitmap?) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val thisWidget = ComponentName(context, LuminaPlayerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget) ?: return

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, song, isPlaying, artwork)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            song: Song?,
            isPlaying: Boolean,
            artwork: Bitmap?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            // Open main activity when tapping widget container
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_container, openPendingIntent)

            // Brand badge tag
            views.setTextViewText(R.id.widget_app_tag, context.getString(R.string.app_name_upper))

            // Track details
            if (song != null) {
                views.setTextViewText(R.id.widget_title, song.title)
                views.setTextViewText(R.id.widget_artist, "${song.displayArtist} · ${song.displayAlbum}")
            } else {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_artist, context.getString(R.string.app_subtitle))
            }

            // Rounded album artwork thumbnail or modern fallback
            if (artwork != null && !artwork.isRecycled) {
                val roundedArtwork = getRoundedCornerBitmap(artwork, cornerRadiusPx = 32f)
                views.setImageViewBitmap(R.id.widget_art, roundedArtwork)
            } else {
                views.setImageViewResource(R.id.widget_art, R.drawable.ic_widget_default_art)
            }

            // Action button PendingIntents
            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                getBroadcastPendingIntent(context, ACTION_PREV, 1)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                getBroadcastPendingIntent(context, ACTION_PLAY_PAUSE, 2)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                getBroadcastPendingIntent(context, ACTION_NEXT, 3)
            )

            // Play / Pause icon state toggle
            val playPauseRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseRes)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getRoundedCornerBitmap(src: Bitmap, cornerRadiusPx: Float): Bitmap {
            return runCatching {
                val size = 160
                val scaled = if (src.width != size || src.height != size) {
                    Bitmap.createScaledBitmap(src, size, size, true)
                } else {
                    src
                }
                val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                }
                val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                if (scaled !== src) {
                    scaled.recycle()
                }
                output
            }.getOrDefault(src)
        }

        private fun getBroadcastPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, LuminaPlayerWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
