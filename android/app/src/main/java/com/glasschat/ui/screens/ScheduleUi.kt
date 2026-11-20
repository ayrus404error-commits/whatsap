package com.glasschat.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasschat.engine.Scheduled
import com.glasschat.engine.ScheduledMessage
import com.glasschat.ui.components.ActionSheet
import com.glasschat.ui.components.SheetAction
import com.glasschat.ui.glass.GlassModal
import com.glasschat.ui.glass.menuGlass
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch
import java.util.Calendar

/** "Send later" choices: quick times plus a date & time picker (10 min – 2 weeks). */
@Composable
fun ScheduleSheet(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val options = remember {
        buildList {
            add("In 10 minutes" to now + Scheduled.MIN_MS + 30_000)
            add("In 1 hour" to now + 60 * 60_000L)
            add("In 3 hours" to now + 3 * 60 * 60_000L)
            val tonight = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            if (tonight - now > Scheduled.MIN_MS) add("Tonight at ${timeLabel(ctx, tonight)}" to tonight)
            val tomorrow = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            add("Tomorrow at ${timeLabel(ctx, tomorrow)}" to tomorrow)
        }
    }
    ActionSheet(
        "Schedule message — it's sent from this phone at that time",
        options.map { (label, at) -> SheetAction(label, Icons.Outlined.Schedule) { onPick(at) } } +
            SheetAction("Pick date & time…", Icons.Outlined.Schedule) { pickScheduleTime(ctx, onPick) },
        onDismiss,
    )
}

/** Android's date then time pickers, limited to 10 minutes – 2 weeks from now. */
fun pickScheduleTime(ctx: Context, onPicked: (Long) -> Unit, onError: (String) -> Unit = {}) {
    val now = System.currentTimeMillis()
    val start = Calendar.getInstance().apply { timeInMillis = now + Scheduled.MIN_MS }
    val d = DatePickerDialog(ctx, { _, y, mo, day ->
        TimePickerDialog(ctx, { _, h, mi ->
            val at = Calendar.getInstance().apply { set(y, mo, day, h, mi, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val err = Scheduled.check(at)
            if (err == null) onPicked(at) else {
                android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show()
                onError(err)
            }
        }, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), DateFormat.is24HourFormat(ctx)).show()
    }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH))
    d.datePicker.minDate = now
    d.datePicker.maxDate = now + Scheduled.MAX_MS
    d.show()
}

fun timeLabel(ctx: Context, at: Long): String = DateFormat.getTimeFormat(ctx).format(java.util.Date(at))

fun whenLabel(ctx: Context, at: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    val days = ((startOfDay(c) - startOfDay(today)) / 86_400_000L).toInt()
    val day = when (days) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date(at))
    }
    return "$day, ${timeLabel(ctx, at)}"
}

private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Small bar above the input: "2 scheduled messages". */
@Composable
fun ScheduledBanner(count: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .menuGlass(shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Schedule, null, tint = p.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (count == 1) "1 scheduled message" else "$count scheduled messages",
            style = Type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = p.label,
        )
    }
}

/** The chat's scheduled messages: send now or cancel. */
@Composable
fun ScheduledList(chat: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by Scheduled.items.collectAsState()
    val list = all.filter { it.chat == chat }
    GlassModal(onDismiss = onDismiss, alignment = Alignment.BottomCenter, dim = 0.22f) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .padding(horizontal = 10.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 8.dp)
                .fillMaxWidth()
                .menuGlass(shape)
                .clip(shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Text(
                "Scheduled messages",
                style = Type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                color = p.label,
                modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 4.dp),
            )
            Text(
                "Sent from this phone at the time shown. Keep the phone on and connected.",
                style = Type.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 10.dp),
            )
            if (list.isEmpty()) {
                Text("Nothing scheduled", style = Type.body, color = p.secondaryLabel, modifier = Modifier.padding(18.dp))
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(list, key = { it.id }) { m ->
                    HorizontalDivider(color = p.separator, thickness = 0.5.dp)
                    ScheduledRow(
                        m,
                        onSendNow = {
                            scope.launch {
                                Scheduled.cancelKeepFiles(ctx, m.id)
                                Scheduled.send(m)
                            }
                        },
                        onReschedule = { pickScheduleTime(ctx, { at -> Scheduled.reschedule(ctx, m.id, at) }) },
                        onCancel = { Scheduled.cancel(ctx, m.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledRow(m: ScheduledMessage, onSendNow: () -> Unit, onReschedule: () -> Unit, onCancel: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onReschedule)) {
            Text(whenLabel(ctx, m.at), style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = p.accent)
            val what = when (m.kind) {
                "image" -> "📷 Photo"
                "video" -> "🎥 Video"
                "document" -> "📄 Document"
                else -> ""
            }
            Text(
                listOf(what, m.text).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Message" },
                style = Type.body,
                color = p.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Tap to change the time", style = Type.caption, color = p.secondaryLabel)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(p.accent).clickable(onClick = onSendNow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowUpward, "Send now", tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(p.label.copy(alpha = 0.1f)).clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Cancel", tint = p.danger, modifier = Modifier.size(19.dp))
        }
    }
}
