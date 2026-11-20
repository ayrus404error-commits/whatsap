package com.glasschat.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Engine
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.GlassAlert
import com.glasschat.ui.components.SectionCard
import com.glasschat.ui.glass.LineIcons
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One round button under the name (Voice, Video, Search…). */
data class ProfileAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * The top of a profile page, like WhatsApp on iPhone: a big round photo,
 * the name in bold, the number, the About line, and round action buttons.
 */
@Composable
fun ProfileHeader(
    jid: String,
    isGroup: Boolean,
    photo: ImageBitmap?,
    name: String,
    lines: List<String>,
    actions: List<ProfileAction>,
    onPhoto: (() -> Unit)?,
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(66.dp))
        Box(
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(p.fill)
                .then(if (onPhoto != null) Modifier.clickable(onClick = onPhoto) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Avatar(jid, isGroup, 132.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            name,
            style = Type.largeTitle.copy(fontSize = 27.sp, fontWeight = FontWeight.Bold),
            color = p.label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        lines.filter { it.isNotBlank() }.forEachIndexed { i, line ->
            Text(
                line,
                style = if (i == 0) Type.rowTitle.copy(fontWeight = FontWeight.Normal) else Type.body,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            actions.forEach { a -> RoundProfileButton(a) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RoundProfileButton(a: ProfileAction) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(p.label.copy(alpha = if (p.dark) 0.12f else 0.07f)).clickable(onClick = a.onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(a.icon, a.label, tint = p.label, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(a.label, style = Type.subhead, color = p.label, maxLines = 1)
    }
}

/**
 * "Media, links and docs" (count), "Free up storage" (size on this phone)
 * and "Starred" (count), in one card.
 */
@Composable
fun MediaRowsCard(jid: String, onMedia: () -> Unit, onStarred: () -> Unit, onMessage: (String) -> Unit) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var mediaCount by remember { mutableIntStateOf(-1) }
    var starred by remember { mutableIntStateOf(-1) }
    var bytes by remember { mutableLongStateOf(-1L) }
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(jid) {
        mediaCount = Engine.chatMediaCounts(jid).values.sum()
        starred = Engine.starred(jid).size
        val all = withContext(Dispatchers.IO) {
            (Engine.chatMedia(jid, "media", 0, 5000) + Engine.chatMedia(jid, "docs", 0, 5000) + Engine.chatMedia(jid, "audio", 0, 5000))
                .mapNotNull { m -> m.local.takeIf { it.isNotBlank() && File(it).exists() } }
                .distinct()
        }
        files = all
        bytes = withContext(Dispatchers.IO) { all.sumOf { File(it).length() } }
    }
    SectionCard {
        InfoRow(LineIcons.Photo, "Media, links and docs", if (mediaCount >= 0) "$mediaCount" else "", onMedia)
        RowDivider()
        InfoRow(LineIcons.Storage, "Free up storage", if (bytes >= 0) Fmt.size(bytes) else "") { if (bytes > 0) confirm = true }
        RowDivider()
        InfoRow(LineIcons.Star, "Starred", if (starred >= 0) "$starred" else "", onStarred)
    }
    if (confirm) {
        val cancel: () -> Unit = { confirm = false }
        val go: () -> Unit = {
            confirm = false
            scope.launch {
                withContext(Dispatchers.IO) { files.forEach { runCatching { File(it).delete() } } }
                com.glasschat.media.MediaState.forget()
                onMessage("Freed ${Fmt.size(bytes)}")
                bytes = 0
                files = emptyList()
            }
        }
        GlassAlert(
            title = "Free up ${Fmt.size(bytes)}?",
            message = "Downloaded photos, videos, voice notes and files of this chat are removed from this phone. The messages stay, and you can download them again.",
            onDismiss = cancel,
            buttons = listOf("Cancel" to cancel, "Free up" to go),
            destructiveIndex = 1,
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.label, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = Type.body, color = p.label, modifier = Modifier.weight(1f))
        Text(value, style = Type.body, color = p.secondaryLabel)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(24.dp))
    }
}
