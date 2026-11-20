package com.glasschat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.glasschat.engine.Engine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Inline reply from a message notification. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chat = intent.getStringExtra(Notifier.EXTRA_CHAT) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        val pending = goAsync()
        Engine.init(context)
        Engine.scope.launch {
            // The engine may still be starting if the app process was asleep.
            repeat(40) { if (!Engine.isLoggedIn) delay(100) }
            Engine.connect()
            Engine.sendText(chat, text, null)
            Engine.markRead(chat)
            Notifier.addOwnReply(context, chat, text)
            delay(4000) // give the message a moment to go out
            pending.finish()
        }
    }
}

class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chat = intent.getStringExtra(Notifier.EXTRA_CHAT) ?: return
        Notifier.cancel(context, chat)
        val pending = goAsync()
        Engine.init(context)
        Engine.scope.launch {
            repeat(40) { if (!Engine.isLoggedIn) delay(100) }
            Engine.connect()
            Engine.markRead(chat)
            delay(3000)
            pending.finish()
        }
    }
}

/** "Play voice" on a message notification: plays it without opening the app. */
class VoicePlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chat = intent.getStringExtra(Notifier.EXTRA_CHAT) ?: return
        val id = intent.getStringExtra(Notifier.EXTRA_MSG) ?: return
        val ctx = context.applicationContext
        val pending = goAsync()
        Engine.init(ctx)
        val key = com.glasschat.media.MediaState.key(chat, id)
        Engine.scope.launch {
            try {
                repeat(40) { if (!Engine.isLoggedIn) delay(100) }
                Engine.connect()
                val path = kotlinx.coroutines.withTimeoutOrNull(8_000) {
                    com.glasschat.media.MediaState.fetch(chat, id).getOrNull()
                }
                if (path == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(ctx, "Couldn't download the voice message", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val started = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    com.glasschat.media.VoicePlayer.toggle(key, path)
                }
                if (started) Engine.markPlayed(chat, id)
                Notifier.refresh(ctx, chat)
            } finally {
                pending.finish()
            }
            // Turn the button back to Play once it finishes.
            com.glasschat.media.VoicePlayer.state.first { !(it.playing && it.key == key) }
            Notifier.refresh(ctx, chat)
        }
    }
}

/** Sends scheduled messages when they're due. */
class ScheduledSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val pending = goAsync()
        Engine.init(ctx)
        Engine.scope.launch {
            try {
                val due = com.glasschat.engine.Scheduled.takeDue(ctx)
                if (due.isNotEmpty()) {
                    repeat(60) { if (!Engine.isLoggedIn) delay(100) }
                    Engine.connect()
                    // Keep the connection up while sending (also starts it if the app was asleep).
                    if (Prefs.background(ctx)) ConnectionService.start(ctx)
                    kotlinx.coroutines.withTimeoutOrNull(20_000) {
                        Engine.conn.first { it == Engine.Conn.Connected }
                    }
                    due.forEach { com.glasschat.engine.Scheduled.send(it) }
                }
                com.glasschat.engine.Scheduled.arm(ctx)
                delay(3_000)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Reconnect after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        Engine.init(ctx)
        // Start the service now, while Android allows it right after boot.
        // The engine connects once it's ready (onStartCommand / Engine.init).
        if (Prefs.background(ctx)) ConnectionService.start(ctx)
        // Alarms are cleared on restart: set the scheduled-message one again.
        com.glasschat.engine.Scheduled.arm(ctx)
    }
}
