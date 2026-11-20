package com.glasschat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glasschat.engine.Engine
import com.glasschat.engine.Message
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.TopEdgeFade
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type

/** Starred messages — of one chat, or of all chats when [jid] is "". */
@Composable
fun StarredScreen(jid: String, nav: Navigator) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val backdrop = remember { Backdrop() }
    val chats by Engine.chats.collectAsState()
    var list by remember { mutableStateOf<List<Message>?>(null) }
    LaunchedEffect(jid) { list = Engine.starred(jid) }
    // Stars removed elsewhere (or from the phone) drop out of the list.
    LaunchedEffect(jid) {
        Engine.events.collect { ev ->
            if (ev is Engine.Event.Updated && (jid.isEmpty() || ev.msg.chat == jid)) list = Engine.starred(jid)
        }
    }
    Box(Modifier.fillMaxSize().background(p.background)) {
        LazyColumn(Modifier.fillMaxSize().backdropSource(backdrop).background(p.background)) {
            item {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(60.dp))
            }
            val l = list
            if (l != null && l.isEmpty()) {
                item {
                    Text(
                        "No starred messages.\nLong-press a message and tap Star to find it here quickly.",
                        style = Type.subhead,
                        color = p.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(36.dp),
                    )
                }
            }
            items(l.orEmpty(), key = { it.chat + "/" + it.id }) { m ->
                Column {
                    if (jid.isEmpty()) {
                        Text(
                            chats.find { it.jid == m.chat }?.name ?: m.chat.substringBefore('@'),
                            style = Type.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = p.secondaryLabel,
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                        )
                    }
                    ResultRow(m, "", ctx) {
                        Engine.requestJump(m.chat, m)
                        nav.push(Route.Chat(m.chat))
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = "Starred messages",
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back") { nav.pop() } },
            right = {},
        )
    }
}
