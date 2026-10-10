package com.example.gobeats.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.gobeats.GobeatsApplication
import com.example.gobeats.MainActivity
import com.example.gobeats.R

class GobeatsAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.gobeats.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.gobeats.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.example.gobeats.widget.ACTION_PREV"
        private const val PREFS_NAME = "gobeats_widget_state"

        fun notifyPlaybackChanged(
            context: Context,
            title: String?,
            artist: String?,
            isPlaying: Boolean
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_title", title ?: "GoBeats Player")
                .putString("last_artist", artist ?: "Tap to play music")
                .putBoolean("is_playing", isPlaying)
                .apply()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, GobeatsAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (appWidgetIds.isNotEmpty()) {
                val views = buildRemoteViews(context, title ?: "GoBeats Player", artist ?: "Tap to play music", isPlaying)
                appWidgetManager.updateAppWidget(appWidgetIds, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_gobeats_player)

            views.setTextViewText(R.id.widget_track_title, title)
            views.setTextViewText(R.id.widget_track_artist, artist)

            val playPauseRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseRes)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // Launch app on clicking widget body
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // Play / Pause
            val playPauseIntent = Intent(context, GobeatsAppWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePending = PendingIntent.getBroadcast(context, 1, playPauseIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePending)

            // Next
            val nextIntent = Intent(context, GobeatsAppWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPending = PendingIntent.getBroadcast(context, 2, nextIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPending)

            // Previous
            val prevIntent = Intent(context, GobeatsAppWidgetProvider::class.java).apply {
                action = ACTION_PREV
            }
            val prevPending = PendingIntent.getBroadcast(context, 3, prevIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPending)

            return views
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString("last_title", "GoBeats Player") ?: "GoBeats Player"
        val artist = prefs.getString("last_artist", "Tap to play music") ?: "Tap to play music"
        val isPlaying = prefs.getBoolean("is_playing", false)

        for (id in appWidgetIds) {
            val views = buildRemoteViews(context, title, artist, isPlaying)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        val app = context.applicationContext as? GobeatsApplication
        val repo = app?.playbackRepository

        when (action) {
            ACTION_PLAY_PAUSE -> {
                repo?.togglePlayPause()
            }
            ACTION_NEXT -> {
                repo?.playNext()
            }
            ACTION_PREV -> {
                repo?.playPrevious()
            }
        }
    }
}
