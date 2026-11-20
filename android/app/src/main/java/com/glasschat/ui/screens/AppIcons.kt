package com.glasschat.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Add
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glasschat.R
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type

/** The app's own launcher icon styles (one launcher alias each). */
object AppIcons {
    data class Style(val key: String, val label: String, @DrawableRes val bg: Int, @DrawableRes val fg: Int)

    val all = listOf(
        Style("Glass", "Default", R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground),
        // Your own picture: replace res/drawable-nodpi/chat_icon_custom.png and rebuild.
        Style("Custom", "Custom", R.drawable.chat_icon_custom, R.drawable.icon_custom_fg),
        Style("Ocean", "Ocean", R.drawable.icon_ocean_bg, R.drawable.ic_launcher_foreground),
        Style("Mint", "Mint", R.drawable.icon_mint_bg, R.drawable.ic_launcher_foreground),
        Style("Sunset", "Sunset", R.drawable.icon_sunset_bg, R.drawable.ic_launcher_foreground),
        Style("Lavender", "Lavender", R.drawable.icon_lavender_bg, R.drawable.ic_launcher_foreground),
        Style("Midnight", "Midnight", R.drawable.icon_midnight_bg, R.drawable.ic_launcher_foreground),
        Style("Gold", "Gold", R.drawable.icon_gold_bg, R.drawable.ic_launcher_foreground),
        Style("Green", "Green glass", R.drawable.icon_green_bg, R.drawable.icon_green_fg),
        Style("Classic", "Classic", R.drawable.icon_classic_bg, R.drawable.icon_classic_fg),
        Style("Outline", "Outline", R.drawable.icon_outline_bg, R.drawable.icon_outline_fg),
        Style("Dark", "Dark", R.drawable.icon_dark_bg, R.drawable.icon_dark_fg),
        Style("Ink", "Ink", R.drawable.icon_ink_bg, R.drawable.icon_ink_fg),
        Style("Aqua", "Aqua", R.drawable.icon_aqua_bg, R.drawable.icon_aqua_fg),
        Style("Purple", "Purple", R.drawable.icon_purple_bg, R.drawable.icon_purple_fg),
    )

    private fun component(ctx: Context, key: String) = ComponentName(ctx.packageName, "com.glasschat.Icon$key")

    fun current(ctx: Context): String = all.firstOrNull { s ->
        when (ctx.packageManager.getComponentEnabledSetting(component(ctx, s.key))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> s.key == "Glass"
            else -> false
        }
    }?.key ?: "Glass"

    /**
     * Puts a home-screen shortcut with your own picture as its icon (Android
     * asks you to confirm). Returns a message to show.
     */
    fun pinCustom(ctx: Context, uri: android.net.Uri): String {
        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            return "Your home screen doesn't allow adding shortcuts with a custom icon."
        }
        val src = runCatching {
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                val long = maxOf(info.size.width, info.size.height)
                if (long > 1024) d.setTargetSize(info.size.width * 1024 / long, info.size.height * 1024 / long)
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: return "Couldn't open that picture."
        // Centre square, with room for the launcher's mask (adaptive icons crop the edges).
        val size = 432
        val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val s = minOf(src.width, src.height)
        c.drawBitmap(
            src,
            android.graphics.Rect((src.width - s) / 2, (src.height - s) / 2, (src.width + s) / 2, (src.height + s) / 2),
            android.graphics.Rect(0, 0, size, size),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        val info = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, "custom-icon-${System.currentTimeMillis()}")
            .setShortLabel(ctx.getString(R.string.app_name))
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithAdaptiveBitmap(out))
            .setIntent(
                android.content.Intent(ctx, com.glasschat.ui.MainActivity::class.java)
                    .setAction(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
            )
            .build()
        val ok = runCatching { androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(ctx, info, null) }.getOrDefault(false)
        return if (ok) "Confirm to add it — the icon with your picture goes on your home screen." else "Couldn't add the shortcut."
    }

    /** Switch the home-screen icon. The launcher may take a few seconds to update. */
    fun set(ctx: Context, key: String) {
        val pm = ctx.packageManager
        // Enable the new one first so there's always a launcher entry.
        pm.setComponentEnabledSetting(component(ctx, key), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        all.filter { it.key != key }.forEach {
            pm.setComponentEnabledSetting(component(ctx, it.key), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        // Post the "Connected" notification again so the phone picks up the new icon
        // for it now instead of showing the old one until the next restart.
        com.glasschat.service.NotifIcon.reset()
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(ctx).cancel(com.glasschat.service.Notifier.SERVICE_ID)
            com.glasschat.service.Notifier.updateService(ctx, com.glasschat.engine.Engine.conn.value, null)
        }
    }
}

/** "App icon" row like Telegram's: tap a style to use it on the home screen. */
@Composable
fun AppIconPicker(onChanged: (String) -> Unit, onMessage: (String) -> Unit = {}) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var chosen by remember { mutableStateOf(AppIcons.current(ctx)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Your own picture as an icon: Android only allows that as a home-screen
    // shortcut (the app's own icon can only be one built into the app).
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            val msg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AppIcons.pinCustom(ctx, uri) }
            onMessage(msg)
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(AppIcons.all, key = { it.key }) { s ->
            val selected = s.key == chosen
            Column(
                Modifier.width(72.dp).clickable {
                    if (!selected) {
                        AppIcons.set(ctx, s.key)
                        chosen = s.key
                        onChanged(s.label)
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val shape = RoundedCornerShape(18.dp)
                Box(
                    Modifier
                        .size(68.dp)
                        .then(if (selected) Modifier.border(2.5.dp, p.accent, RoundedCornerShape(21.dp)) else Modifier)
                        .padding(4.dp)
                        .clip(shape)
                        .background(Color.White),
                ) {
                    // The adaptive icon's layers, cropped like a launcher would.
                    Image(painterResource(s.bg), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Image(painterResource(s.fg), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(0.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    s.label,
                    style = Type.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (selected) p.accentText else p.label,
                )
            }
        }
        item(key = "custom") {
            Column(
                Modifier.width(72.dp).clickable {
                    pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(68.dp).padding(4.dp).clip(RoundedCornerShape(18.dp)).background(p.label.copy(alpha = 0.08f))
                        .border(1.dp, p.separator, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Add, null, tint = p.label, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("Your photo", style = Type.footnote, color = p.label)
            }
        }
    }
}
