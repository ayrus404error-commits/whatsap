package com.glasschat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.glasschat.engine.Engine
import com.glasschat.engine.ChatLists
import com.glasschat.service.CrashLog
import com.glasschat.service.DeliveryLog
import com.glasschat.service.Uptime
import com.glasschat.service.OemSettings
import com.glasschat.ui.theme.Appearance

class GlassChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DeliveryLog.init(this)
        CrashLog.install(this)
        Appearance.init(this)
        ChatLists.init(this)
        com.glasschat.engine.ChatLock.init(this)
        // If this shows up while you weren't opening the app, the phone had
        // killed it and something (boot, heartbeat, a notification action) restarted it.
        DeliveryLog.add("app process started · ${OemSettings.statusLine(this)}")
        Uptime.init(this)
        Engine.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = Engine.setForeground(true)
            override fun onStop(owner: LifecycleOwner) = Engine.setForeground(false)
        })
    }
}
