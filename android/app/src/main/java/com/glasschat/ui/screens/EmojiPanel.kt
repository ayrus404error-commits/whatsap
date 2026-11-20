package com.glasschat.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Engine
import com.glasschat.engine.Sticker
import com.glasschat.media.rememberImage
import com.glasschat.ui.glass.frosted
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch

/** iPhone-style emoji keyboard plus your stickers, in place of the keyboard. */
@Composable
fun EmojiStickerPanel(
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onSticker: (String) -> Unit,
    onStickerFile: (String) -> Unit,
    emojiOnly: Boolean = false,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(320.dp)
            .clip(shape)
            .frosted(shape, if (p.dark) Color(0xF01C1C1E) else Color(0xF5F7F7F7), 0.5f),
    ) {
        // Emoji | Stickers
        if (!emojiOnly) Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp).clip(RoundedCornerShape(50)).background(p.fill).padding(3.dp),
        ) {
            listOf("Emoji", "Stickers").forEachIndexed { i, label ->
                Text(
                    label,
                    style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = if (tab == i) p.label else p.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (tab == i) (if (p.dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                        .clickable { tab = i }
                        .padding(vertical = 6.dp),
                )
            }
        }
        if (tab == 0) {
            EmojiGrid(onEmoji = { e -> EmojiRecents.add(ctx, e); onEmoji(e) }, onBackspace = onBackspace)
        } else {
            StickerGrid(onSticker, onStickerFile)
        }
    }
}

@Composable
private fun EmojiGrid(onEmoji: (String) -> Unit, onBackspace: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recents = remember { EmojiRecents.get(ctx) }
    val sections = remember(recents) {
        buildList {
            if (recents.isNotEmpty()) add(EmojiSection("🕘", "Frequently used", recents))
            addAll(EmojiData.sections)
        }
    }
    // Index of each section's header in the grid, to jump from the bottom bar.
    val headerIndex = remember(sections) {
        var i = 0
        sections.map { s -> i.also { i += 1 + s.emojis.size } }
    }
    val grid = rememberLazyGridState()
    val current by remember(headerIndex) {
        derivedStateOf { headerIndex.indexOfLast { it <= grid.firstVisibleItemIndex }.coerceAtLeast(0) }
    }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            state = grid,
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 52.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            sections.forEach { s ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-" + s.title) {
                    Text(
                        s.title.uppercase(),
                        style = Type.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                        color = p.secondaryLabel,
                        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp),
                    )
                }
                items(s.emojis.size, key = { "${s.title}-$it" }) { i ->
                    val e = s.emojis[i]
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { onEmoji(e) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e, fontSize = 28.sp)
                    }
                }
            }
        }
        // Category bar with backspace, like the iPhone keyboard.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(if (p.dark) Color(0xF02C2C2E) else Color(0xF5EDEDF0))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEachIndexed { i, s ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (i == current) p.fill else Color.Transparent)
                        .clickable { scope.launch { grid.scrollToItem(headerIndex[i]) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.icon, fontSize = 17.sp)
                }
            }
            Box(
                Modifier.size(width = 44.dp, height = 32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBackspace),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = p.label, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun StickerGrid(onPick: (String) -> Unit, onFile: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stickers by remember { mutableStateOf<List<Sticker>?>(null) }
    var mine by remember { mutableStateOf(MyStickers.list(ctx)) }
    var making by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { stickers = Engine.stickers() }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            making = true
            scope.launch {
                MyStickers.fromPhoto(ctx, uri)
                mine = MyStickers.list(ctx)
                making = false
            }
        }
    }
    val list = stickers.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "h-mine") { GridTitle("My stickers") }
        item(key = "add") {
            Box(
                Modifier.aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(14.dp)).background(p.fill).clickable(enabled = !making) {
                    runCatching {
                        picker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (making) {
                    CircularProgressIndicator(color = p.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+", fontSize = 26.sp, color = p.accentText)
                        Text("From photo", style = Type.caption.copy(fontSize = 10.sp), color = p.secondaryLabel)
                    }
                }
            }
        }
        items(mine.size, key = { "mine-" + mine[it] }) { i ->
            val path = mine[i]
            val img = rememberImage(path, 256)
            Box(
                Modifier.aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(12.dp)).clickable { onFile(path) },
                contentAlignment = Alignment.Center,
            ) {
                if (img != null) Image(img, "Sticker", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        if (list.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-recent") { GridTitle("Recent and favourites") }
            items(list.size, key = { list[it].id }) { i -> StickerTile(list[i], onPick) }
        } else if (stickers == null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = p.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "h-emoji") { GridTitle("Emoji stickers") }
        items(MyStickers.emojiPack.size, key = { "e-" + MyStickers.emojiPack[it] }) { i ->
            val e = MyStickers.emojiPack[i]
            Box(
                Modifier.aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(12.dp)).clickable {
                    scope.launch { MyStickers.fromEmoji(ctx, e)?.let(onFile) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(e, fontSize = 44.sp)
            }
        }
    }
}

@Composable
private fun GridTitle(text: String) {
    Text(
        text.uppercase(),
        style = Type.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
        color = LocalPalette.current.secondaryLabel,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** Stickers you make on this phone: from a photo, or a big emoji. 512×512 WebP. */
object MyStickers {
    val emojiPack = listOf(
        "😂", "🤣", "😍", "🥰", "😘", "😎", "🤩", "🥳", "😭", "😡", "🤯", "🥺", "😴", "🤔", "🙄", "😱",
        "👍", "👎", "🙏", "👏", "💪", "🤝", "👌", "✌️", "❤️", "💔", "🔥", "💯", "🎉", "🌹", "☕", "🍕",
    )

    private fun dir(ctx: Context) = java.io.File(ctx.filesDir, "my_stickers").apply { mkdirs() }

    fun list(ctx: Context): List<String> =
        dir(ctx).listFiles()?.filter { it.extension == "webp" }?.sortedByDescending { it.lastModified() }?.map { it.absolutePath } ?: emptyList()

    private fun save(ctx: Context, bmp: android.graphics.Bitmap, file: java.io.File): String {
        java.io.FileOutputStream(file).use { out ->
            @Suppress("DEPRECATION")
            val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY else android.graphics.Bitmap.CompressFormat.WEBP
            bmp.compress(fmt, 80, out)
        }
        return file.absolutePath
    }

    /** The middle of a photo, rounded, on a transparent 512×512 canvas. */
    suspend fun fromPhoto(ctx: Context, uri: android.net.Uri): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val src = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                val long = maxOf(info.size.width, info.size.height)
                if (long > 1536) {
                    val k = 1536f / long
                    d.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                }
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val side = minOf(src.width, src.height)
            val out = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
            val rect = android.graphics.RectF(16f, 16f, 496f, 496f)
            c.drawRoundRect(rect, 72f, 72f, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            val l = (src.width - side) / 2
            val t = (src.height - side) / 2
            c.drawBitmap(src, android.graphics.Rect(l, t, l + side, t + side), rect, paint)
            save(ctx, out, java.io.File(dir(ctx), "photo_${System.currentTimeMillis()}.webp"))
        }.getOrNull()
    }

    /** A big emoji drawn as a sticker (made once, then reused). */
    suspend fun fromEmoji(ctx: Context, emoji: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val f = java.io.File(ctx.cacheDir, "emoji_sticker_${com.glasschat.ui.theme.AppFont.typeface?.hashCode() ?: 0}_${emoji.codePoints().toArray().joinToString("_")}.webp")
            if (f.length() > 0) return@runCatching f.absolutePath
            val out = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 400f
                textAlign = android.graphics.Paint.Align.CENTER
                com.glasschat.ui.theme.AppFont.typeface?.let { typeface = it }
            }
            val fm = paint.fontMetrics
            c.drawText(emoji, 256f, 256f - (fm.ascent + fm.descent) / 2f, paint)
            save(ctx, out, f)
        }.getOrNull()
    }
}

@Composable
private fun StickerTile(s: Sticker, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    var path by remember(s.id) { mutableStateOf<String?>(null) }
    var failed by remember(s.id) { mutableStateOf(false) }
    LaunchedEffect(s.id) {
        Engine.downloadSticker(s.id).onSuccess { path = it }.onFailure { failed = true }
    }
    val img = rememberImage(path, 256)
    Box(
        Modifier.aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = path != null) { onPick(s.id) },
        contentAlignment = Alignment.Center,
    ) {
        when {
            img != null -> Image(img, "Sticker", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            failed -> Icon(Icons.Outlined.EmojiEmotions, null, tint = p.tertiaryLabel, modifier = Modifier.size(22.dp))
            else -> CircularProgressIndicator(color = p.tertiaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        }
    }
}

/** Inserts [text] at the cursor. */
fun TextFieldValue.insert(text: String): TextFieldValue {
    val start = selection.min.coerceIn(0, this.text.length)
    val end = selection.max.coerceIn(0, this.text.length)
    val t = this.text.substring(0, start) + text + this.text.substring(end)
    return TextFieldValue(t, TextRange(start + text.length))
}

/** Deletes the character (whole emoji included) before the cursor. */
fun TextFieldValue.backspace(): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    if (start != end) return TextFieldValue(text.substring(0, start) + text.substring(end), TextRange(start))
    if (start == 0) return this
    val it = android.icu.text.BreakIterator.getCharacterInstance()
    it.setText(text)
    val prev = it.preceding(start).let { b -> if (b == android.icu.text.BreakIterator.DONE) start - 1 else b }
    return TextFieldValue(text.substring(0, prev) + text.substring(start), TextRange(prev))
}

private object EmojiRecents {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("emoji", Context.MODE_PRIVATE)

    fun get(ctx: Context): List<String> =
        sp(ctx).getString("recent", "")!!.split('\u0001').filter { it.isNotEmpty() }

    fun add(ctx: Context, e: String) {
        val list = (listOf(e) + get(ctx).filter { it != e }).take(24)
        sp(ctx).edit().putString("recent", list.joinToString("\u0001")).apply()
    }
}

private class EmojiSection(val icon: String, val title: String, val emojis: List<String>)

private object EmojiData {
    private val paint = android.graphics.Paint()

    /** Newer emoji show only when this phone's font can draw them (no empty boxes). */
    private fun s(icon: String, title: String, all: String, newer: String = "") = EmojiSection(
        icon,
        title,
        all.trim().split(Regex("\\s+")) +
            newer.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && runCatching { paint.hasGlyph(it) }.getOrDefault(false) },
    )

    val sections = listOf(
        s(
            "😀", "Smileys & People",
            """
            😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 🫠 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🫢 🫣 🤫 🤔 🫡
            🤐 🤨 😐 😑 😶 🫥 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 😎 🤓 🧐 😕 🫤 😟
            🙁 ☹️ 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽
            👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊 👋 🤚 🖐️ ✋ 🖖 🫱 🫲 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 🖕 👇 ☝️
            🫵 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🦵 🦶 👂 👃 🧠 👀 👁️ 👅 👄 🫦 👶 🧒 👦 👧 🧑 👱 👨
            🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵️ 💂 👷 🤴 👸 👳 👲 🧕 🤵 👰 🤰 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛
            🧜 🧝 🧞 🧟 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 👯 🧖 🧗 🤺 🏇 ⛷️ 🏂 🏌️ 🏄 🚣 🏊 ⛹️ 🏋️ 🚴 🤸 🤼 🤽 🤾 🤹 🧘 🛀 🛌 👭 👫
            👬 💏 💑 👪 🗣️ 👤 👥 🫂 👣 💋 💌 💘 💝 💖 💗 💓 💞 💕 💟 ❣️ 💔 ❤️ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 💯 💢 💥 💫 💦 💨
            🕳️ 💬 👁️‍🗨️ 🗨️ 🗯️ 💭 💤
            """,
            newer = "🫨 🫩 🫪 😮‍💨 😵‍💫 😶‍🌫️ 🫷 🫸 🧑‍🩰 ❤️‍🔥 ❤️‍🩹 🩷 🩵 🩶",
        ),
        s(
            "🐻", "Animals & Nature",
            """
            🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐻‍❄️ 🐨 🐯 🦁 🐮 🐷 🐽 🐸 🐵 🐒 🐔 🐧 🐦 🐤 🐣 🐥 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🪱 🐛 🦋
            🐌 🐞 🐜 🪰 🪲 🪳 🦟 🦗 🕷️ 🕸️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🦧 🦣 🐘
            🦛 🦏 🐪 🐫 🦒 🦘 🦬 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🦮 🐈 🐈‍⬛ 🪶 🐓 🦃 🦤 🦚 🦜 🦢 🦩 🕊️ 🐇 🦝 🦨 🦡 🦫 🦦
            🦥 🐁 🐀 🐿️ 🦔 🐾 🐉 🐲 🌵 🎄 🌲 🌳 🌴 🪵 🌱 🌿 ☘️ 🍀 🎍 🪴 🎋 🍃 🍂 🍁 🍄 🐚 🪨 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻
            🌞 🌝 🌛 🌜 🌚 🌕 🌖 🌗 🌘 🌑 🌒 🌓 🌔 🌙 🌎 🌍 🌏 🪐 💫 ⭐ 🌟 ✨ ⚡ ☄️ 💥 🔥 🌪️ 🌈 ☀️ 🌤️ ⛅ 🌥️ ☁️ 🌦️ 🌧️ ⛈️
            🌩️ 🌨️ ❄️ ☃️ ⛄ 🌬️ 💨 💧 💦 ☔ ☂️ 🌊 🌫️
            """,
            newer = "🫎 🫏 🪽 🐦‍⬛ 🪿 🪼 🪻 🐦‍🔥 🪾 🫍 🫈",
        ),
        s(
            "🍔", "Food & Drink",
            """
            🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🫑 🌽 🥕 🫒 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖
            🥨 🧀 🥚 🍳 🧈 🥞 🧇 🥓 🥩 🍗 🍖 🦴 🌭 🍔 🍟 🍕 🫓 🥪 🥙 🧆 🌮 🌯 🫔 🥗 🥘 🫕 🥫 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🦪 🍤
            🍙 🍚 🍘 🍥 🥠 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 🫖 ☕ 🍵 🧃 🥤 🧋 🍶 🍺 🍻
            🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊 🥄 🍴 🍽️ 🥣 🥡 🥢 🧂
            """,
            newer = "🫚 🫛 🍋‍🟩 🍄‍🟫 🫜",
        ),
        s(
            "⚽", "Activity",
            """
            ⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🪃 🥅 ⛳ 🪁 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 🏆 🥇 🥈
            🥉 🏅 🎖️ 🏵️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭 🩰 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🪘 🎷 🎺 🪗 🎸 🪕 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰 🧩 🎉 🎊 🎈 🎁
            🎀 🪅 🪆 🎏 🎐 🧧 🎑 🎃 🎆 🎇 🧨 🪔
            """,
            newer = "🪇 🪈 🪉 🪊 🫯",
        ),
        s(
            "🚗", "Travel & Places",
            """
            🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🦯 🦽 🦼 🛴 🚲 🛵 🏍️ 🛺 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅
            🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🛰️ 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ 🪝 ⛽ 🚧 🚦 🚥 🚏 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️
            🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🛖 🏠 🏡 🏘️ 🏚️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛️ ⛪ 🕌 🕍
            🛕 🕋 ⛩️ 🛤️ 🛣️ 🗾 🎑 🏞️ 🌅 🌄 🌠 🎇 🎆 🌇 🌆 🏙️ 🌃 🌌 🌉 🌁
            """,
            newer = "🛜 🛘",
        ),
        s(
            "💡", "Objects",
            """
            ⌚ 📱 📲 💻 ⌨️ 🖥️ 🖨️ 🖱️ 🖲️ 🕹️ 🗜️ 💽 💾 💿 📀 📼 📷 📸 📹 🎥 📽️ 🎞️ 📞 ☎️ 📟 📠 📺 📻 🎙️ 🎚️ 🎛️ 🧭 ⏱️ ⏲️ ⏰ 🕰️
            ⌛ ⏳ 📡 🔋 🪫 🔌 💡 🔦 🕯️ 🪔 🧯 🛢️ 💸 💵 💴 💶 💷 🪙 💰 💳 💎 ⚖️ 🪜 🧰 🪛 🔧 🔨 ⚒️ 🛠️ ⛏️ 🪚 🔩 ⚙️ 🪤 🧱 ⛓️
            🧲 🔫 💣 🧨 🪓 🔪 🗡️ ⚔️ 🛡️ 🚬 ⚰️ 🪦 ⚱️ 🏺 🔮 📿 🧿 💈 ⚗️ 🔭 🔬 🕳️ 🩹 🩺 💊 💉 🩸 🧬 🦠 🧫 🧪 🌡️ 🧹 🪠 🧺 🧻
            🚽 🚰 🚿 🛁 🛀 🧼 🪥 🪒 🧽 🪣 🧴 🛎️ 🔑 🗝️ 🚪 🪑 🛋️ 🛏️ 🛌 🧸 🪆 🖼️ 🪞 🪟 🛍️ 🛒 🎁 🎈 🎏 🎀 🪄 🪅 🎊 🎉 🎎 🏮
            🎐 🧧 ✉️ 📩 📨 📧 💌 📥 📤 📦 🏷️ 🪧 📪 📫 📬 📭 📮 📯 📜 📃 📄 📑 🧾 📊 📈 📉 🗒️ 🗓️ 📆 📅 🗑️ 📇 🗃️ 🗳️ 🗄️ 📋
            📁 📂 🗂️ 🗞️ 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 🧷 🔗 📎 🖇️ 📐 📏 🧮 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔎 🔏 🔐
            🔒 🔓
            """,
            newer = "🪭 🪮 🪯 🪏 🫆 🪎 🫟",
        ),
        s(
            "❤️", "Symbols",
            """
            ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ 🛐 ⛎ ♈ ♉ ♊ ♋ ♌
            ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️ 🉑 ☢️ ☣️ 📴 📳 🈶 🈚 🈸 🈺 🈷️ ✴️ 🆚 💮 🉐 ㊙️ ㊗️ 🈴 🈵 🈹 🈲 🅰️ 🅱️ 🆎 🆑 🅾️ 🆘 ❌
            ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ 🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕ ❓ ❔ ‼️ ⁉️ 🔅 🔆 〽️ ⚠️ 🚸 🔱 ⚜️ 🔰 ♻️ ✅ 🈯 💹 ❇️ ✳️ ❎
            🌐 💠 Ⓜ️ 🌀 💤 🏧 🚾 ♿ 🅿️ 🛗 🈳 🈂️ 🛂 🛃 🛄 🛅 🚹 🚺 🚼 ⚧️ 🚻 🚮 🎦 📶 🈁 🔣 ℹ️ 🔤 🔡 🔠 🆖 🆗 🆙 🆒 🆕 🆓
            0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟 🔢 #️⃣ *️⃣ ⏏️ ▶️ ⏸️ ⏯️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ ⏫ ⏬ ◀️ 🔼 🔽 ➡️ ⬅️ ⬆️ ⬇️ ↗️ ↘️ ↙️
            ↖️ ↕️ ↔️ ↪️ ↩️ ⤴️ ⤵️ 🔀 🔁 🔂 🔄 🔃 🎵 🎶 ➕ ➖ ➗ ✖️ 🟰 ♾️ 💲 💱 ™️ ©️ ®️ 〰️ ➰ ➿ 🔚 🔙 🔛 🔝 🔜 ✔️ ☑️ 🔘
            🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🔺 🔻 🔸 🔹 🔶 🔷 🔳 🔲 ▪️ ▫️ ◾ ◽ ◼️ ◻️ 🟥 🟧 🟨 🟩 🟦 🟪 ⬛ ⬜ 🟫 🔈 🔇 🔉 🔊 🔔 🔕
            📣 📢 💬 💭 🗯️ ♠️ ♣️ ♥️ ♦️ 🃏 🎴 🀄 🕐 🕑 🕒 🕓 🕔 🕕 🕖 🕗 🕘 🕙 🕚 🕛
            """,
        ),
        s(
            "🏳️", "Flags",
            """
            🏳️ 🏴 🏁 🚩 🏳️‍🌈 🏳️‍⚧️ 🇮🇳 🇺🇸 🇬🇧 🇨🇦 🇦🇺 🇳🇿 🇳🇵 🇧🇹 🇧🇩 🇱🇰 🇵🇰 🇦🇫 🇲🇻 🇨🇳 🇯🇵 🇰🇷 🇸🇬 🇲🇾 🇹🇭 🇻🇳 🇮🇩 🇵🇭 🇦🇪 🇸🇦 🇶🇦 🇰🇼 🇴🇲 🇧🇭 🇮🇱 🇹🇷
            🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇵🇹 🇳🇱 🇧🇪 🇨🇭 🇦🇹 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇮🇪 🇵🇱 🇷🇺 🇺🇦 🇬🇷 🇪🇬 🇿🇦 🇳🇬 🇰🇪 🇧🇷 🇦🇷 🇲🇽 🇨🇴 🇨🇱 🇵🇪 🇪🇺 🇺🇳
            """,
        ),
    )
}
