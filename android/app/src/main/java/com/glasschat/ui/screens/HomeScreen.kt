package com.glasschat.ui.screens

import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.foundation.layout.imePadding
import com.glasschat.ui.glass.menuGlass
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.glasschat.engine.ChatLock
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasschat.engine.Chat
import com.glasschat.engine.Engine
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.glasschat.service.Uptime
import com.glasschat.service.Downtime
import com.glasschat.engine.ChatLists
import com.glasschat.service.OemSettings
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.DisposableEffect
import com.glasschat.engine.StatusGroup
import com.glasschat.engine.Typing
import com.glasschat.media.MediaPrep
import com.glasschat.media.PendingMedia
import com.glasschat.media.rememberMediaPickers
import com.glasschat.engine.previewFor
import com.glasschat.ui.Navigator
import com.glasschat.ui.Route
import com.glasschat.ui.components.ActionSheet
import com.glasschat.ui.components.Avatar
import com.glasschat.ui.components.Fmt
import com.glasschat.ui.components.SheetAction
import com.glasschat.ui.components.Ticks
import com.glasschat.ui.components.Toast
import com.glasschat.ui.components.TopEdgeFade
import kotlinx.coroutines.delay
import com.glasschat.ui.components.Wallpapers
import com.glasschat.ui.components.Wallpaper
import com.glasschat.ui.glass.Backdrop
import com.glasschat.ui.glass.FilterChip
import com.glasschat.ui.glass.GlassCircleButton
import com.glasschat.ui.glass.GlassTabBar
import com.glasschat.ui.glass.TabItem
import com.glasschat.ui.glass.backdropSource
import com.glasschat.ui.glass.frosted
import com.glasschat.ui.glass.glassShadow
import com.glasschat.ui.theme.LocalChatStyle
import com.glasschat.ui.theme.LocalPalette
import com.glasschat.ui.theme.Type
import kotlinx.coroutines.launch

/** The Home tab last shown (0 Updates, 1 Chats, 2 Settings). */
object HomeTabs {
    @Volatile var current = 1
}

@Composable
fun HomeScreen(nav: Navigator) {
    val p = LocalPalette.current
    // The tab bar's glass reads the swiping pages; each page has its own
    // backdrop for its own header buttons.
    val barBackdrop = remember { Backdrop() }
    val updatesBackdrop = remember { Backdrop() }
    val chatsBackdrop = remember { Backdrop() }
    val settingsBackdrop = remember { Backdrop() }
    // Tabs: 0 Updates, 1 Chats, 2 Settings — swipe between them, or tap.
    // Remembered for the app's lifetime, so coming back from a Settings page
    // returns to Settings (not Chats).
    val pager = rememberPagerState(initialPage = HomeTabs.current) { 3 }
    LaunchedEffect(pager.currentPage) { HomeTabs.current = pager.currentPage }
    val chats by Engine.chats.collectAsState()
    var menuChat by remember { mutableStateOf<Chat?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val unread = chats.count { it.hasUnread && !it.archived }
    val style = LocalChatStyle.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchRequest by remember { mutableIntStateOf(0) }

    // Status (Updates tab) overlays live here so they cover the tab bar.
    var story by remember { mutableStateOf<Pair<List<StatusGroup>, Int>?>(null) }
    var textComposer by remember { mutableStateOf(false) }
    var statusAddSheet by remember { mutableStateOf(false) }
    var statusCamera by remember { mutableStateOf(false) }
    var statusVoice by remember { mutableStateOf(false) }
    var statusLayout by remember { mutableStateOf(false) }
    var statusPreview by remember { mutableStateOf<List<PendingMedia>?>(null) }
    var statusAudience by remember { mutableStateOf("My status") }
    val statusPickers = rememberMediaPickers(onMedia = { statusPreview = it })
    val statusVersion by Engine.statusVersion.collectAsState()
    var newStatuses by remember { mutableIntStateOf(0) }
    LaunchedEffect(statusVersion) { newStatuses = Engine.statuses().count { !it.mine && it.unviewed > 0 } }
    // "Status (11 excluded)": who a new update goes to, from WhatsApp's own setting.
    LaunchedEffect(statusPreview != null) {
        if (statusPreview != null) {
            Engine.statusPrivacy().onSuccess { (type, n) ->
                statusAudience = when (type) {
                    "blacklist" -> "Status ($n excluded)"
                    "whitelist" -> "Status ($n included)"
                    else -> "Status (my contacts)"
                }
            }
        }
    }
    fun goTo(page: Int) {
        scope.launch { pager.animateScrollToPage(page) }
    }
    // Back from Updates or Settings goes to Chats first; back on Chats closes the app.
    androidx.activity.compose.BackHandler(enabled = pager.currentPage != 1) { goTo(1) }

    // The camera at the top of Chats: take or pick something, then choose who gets it.
    var chatCamera by remember { mutableStateOf(false) }
    var cameraMedia by remember { mutableStateOf<List<PendingMedia>?>(null) }
    var cameraTarget by remember { mutableStateOf<Chat?>(null) }

    Box(Modifier.fillMaxSize().background(p.background)) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(barBackdrop)
                .then(if (menuChat != null && Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier),
        ) { page ->
            when (page) {
                0 -> UpdatesTab(
                    updatesBackdrop,
                    nav,
                    onOpen = { groups, i -> story = groups to i },
                    onAdd = { statusAddSheet = true },
                )
                1 -> ChatsTab(
                    nav,
                    chatsBackdrop,
                    onLongPress = { menuChat = it },
                    onCamera = { chatCamera = true },
                    searchRequest = searchRequest,
                    onStatus = { sender ->
                        // Open that person's status updates, like tapping a ringed photo in WhatsApp.
                        scope.launch {
                            val groups = Engine.statuses().filter { !it.mine }
                            val i = groups.indexOfFirst { it.sender == sender }
                            if (i >= 0) story = groups to i
                        }
                    },
                )
                else -> SettingsTab(nav, settingsBackdrop)
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxWidth(style.tabWidth.coerceIn(0.5f, 1f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTabBar(
                backdrop = barBackdrop,
                items = listOf(
                    TabItem("Updates", com.glasschat.ui.glass.TabIcons.Updates, com.glasschat.ui.glass.TabIcons.UpdatesSelected, badge = newStatuses),
                    TabItem("Chats", com.glasschat.ui.glass.TabIcons.Chats, com.glasschat.ui.glass.TabIcons.ChatsSelected, badge = unread),
                    TabItem("Settings", Icons.Outlined.Settings, Icons.Outlined.Settings),
                ),
                selected = pager.currentPage,
                onSelect = { goTo(it) },
                height = style.tabHeight.dp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            // Search, as its own glass circle next to the bar (like iOS 26).
            GlassCircleButton(barBackdrop, Icons.Rounded.Search, "Search", size = style.tabHeight.dp.coerceIn(44.dp, 72.dp)) {
                nav.push(Route.Search)
            }
        }
        menuChat?.let { c -> ChatContextMenu(c, onDismiss = { menuChat = null }, onMessage = { toast = it }, onOpenInfo = { nav.push(Route.ChatInfo(c.jid)) }) }
        if (statusAddSheet) {
            AddStatusSheet(
                onText = { statusAddSheet = false; textComposer = true },
                onVoice = { statusAddSheet = false; statusVoice = true },
                onLayout = { statusAddSheet = false; statusLayout = true },
                onCamera = { statusAddSheet = false; statusCamera = true },
                onLibrary = { statusAddSheet = false; statusPickers.pickGallery() },
                onMedia = { statusAddSheet = false; statusPreview = it },
                onClose = { statusAddSheet = false },
            )
        }
        if (statusVoice) {
            VoiceStatusRecorder(onPosted = { statusVoice = false; goTo(0); toast = "Voice status posted" }, onClose = { statusVoice = false })
        }
        if (statusLayout) {
            LayoutMaker(onDone = { statusLayout = false; statusPreview = it }, onClose = { statusLayout = false })
        }
        if (chatCamera) {
            CameraCapture(
                onMedia = { chatCamera = false; cameraMedia = it },
                onClose = { chatCamera = false },
            )
        }
        cameraMedia?.let { items ->
            val target = cameraTarget
            if (target == null) {
                SendToPicker(
                    onPick = { cameraTarget = it },
                    onStatus = { cameraMedia = null; statusPreview = items },
                    onClose = { cameraMedia = null },
                )
            } else {
                SendPreview(
                    items,
                    target = target.name,
                    onSend = { cap, edited ->
                        cameraMedia = null
                        cameraTarget = null
                        scope.launch {
                            if (!MediaPrep.sendPicked(ctx, target.jid, edited, cap, null)) toast = "Some files couldn't be read" else toast = "Sent to ${target.name}"
                        }
                    },
                    allowViewOnce = true,
                    onCancel = { cameraTarget = null },
                )
            }
        }
        if (statusCamera) {
            CameraCapture(
                onMedia = { statusCamera = false; statusPreview = it },
                onClose = { statusCamera = false },
            )
        }
        statusPreview?.let { items ->
            SendPreview(
                items,
                target = statusAudience,
                onSend = { cap, edited ->
                    statusPreview = null
                    goTo(0)
                    scope.launch {
                        if (!MediaPrep.sendPicked(ctx, Engine.STATUS_CHAT, edited, cap, null)) toast = "Some files couldn't be read"
                    }
                },
                onCancel = { statusPreview = null },
            )
        }
        if (textComposer) {
            TextStatusComposer(
                onPost = { text, bg, font ->
                    textComposer = false
                    goTo(0)
                    Engine.postTextStatus(text, bg, font)
                },
                onClose = { textComposer = false },
            )
        }
        story?.let { (groups, i) -> StatusViewer(groups, i, onClose = { story = null }) }
        Toast(toast) { toast = null }
    }
}

// -----------------------------------------------------------------------------
// Chats tab

@Composable
fun ChatsTab(nav: Navigator, backdrop: Backdrop, onLongPress: (Chat) -> Unit, onCamera: () -> Unit, searchRequest: Int = 0, onStatus: (String) -> Unit = {}) {
    val draftCtx = LocalContext.current
    LaunchedEffect(Unit) { com.glasschat.ui.ChatDrafts.load(draftCtx) }
    val p = LocalPalette.current
    val chats by Engine.chats.collectAsState()
    val typing by Engine.typing.collectAsState()
    val history by Engine.history.collectAsState()
    val conn by Engine.conn.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var menuSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val org by ChatLists.state.collectAsState()
    val lock by ChatLock.settings.collectAsState()
    val focusManager = LocalFocusManager.current
    var newList by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    // The search button next to the tab bar: jump to the top and type.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val visible = remember(chats, query, filter, org) {
        val q = query.trim().lowercase()
        chats.asSequence()
            .filter { !it.archived }
            .filter {
                when {
                    filter == "unread" -> it.hasUnread
                    filter == "groups" -> it.isGroup
                    filter == "fav" -> it.jid in org.favorites
                    filter.startsWith("list:") -> org.lists.firstOrNull { l -> "list:" + l.id == filter }?.chats?.contains(it.jid) == true
                    else -> true
                }
            }
            // Locked chats only show behind their own lock.
            .filter { it.jid !in org.locked }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.replace(" ", "").contains(q.replace(" ", "")) || it.lastText.lowercase().contains(q) }
            .toList()
    }
    val archivedCount = remember(chats) { chats.count { it.archived } }
    val titleCollapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 || (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset > 30) } }
    // The shade behind the header only appears once you scroll up.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12 } }
    val shade by animateFloatAsState(if (scrolled) 1f else 0f, label = "shade")

    // Warn when the phone is set up in a way that stops background delivery.
    val ctx = LocalContext.current
    var backgroundOk by remember { mutableStateOf(true) }
    val lifecycle = (ctx as? LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        backgroundOk = OemSettings.allClear(ctx)
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) backgroundOk = OemSettings.allClear(ctx) }
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }

    // When the phone stopped the app in the background, say so (and how to fix it).
    val gaps by Uptime.gaps.collectAsState()
    var downtimeDismissed by remember { mutableStateOf(false) }
    val downtime = remember(gaps, downtimeDismissed) { if (downtimeDismissed) null else Uptime.unseen(ctx, gaps) }

    // "Connecting…" only when a reconnect takes a while, not for a quick blip.
    var slowConnect by remember { mutableStateOf(false) }
    LaunchedEffect(conn) {
        slowConnect = false
        if (conn == Engine.Conn.Connecting) {
            delay(3000)
            slowConnect = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
        Wallpaper(Modifier.fillMaxSize(), chat = Wallpapers.HOME, forList = true)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            item(key = "top") {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(56.dp))
            }
            item(key = "title") {
                Text("Chats", style = Type.largeTitle, color = p.label, modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp))
            }
            item(key = "search") {
                // Tapping it opens the full search page, like WhatsApp.
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(p.fill)
                        .clickable { nav.push(Route.Search) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = p.secondaryLabel, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search", style = Type.body, color = p.secondaryLabel)
                }
            }
            item(key = "filters") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip("All", filter == "all") { filter = "all" }
                    FilterChip("Unread", filter == "unread") { filter = "unread" }
                    FilterChip("Favorites", filter == "fav") { filter = "fav" }
                    FilterChip("Groups", filter == "groups") { filter = "groups" }
                    if (archivedCount > 0) FilterChip("Archived $archivedCount", false) { nav.push(Route.Archived) }
                    org.lists.forEach { l -> FilterChip(l.name, filter == "list:" + l.id) { filter = "list:" + l.id } }
                    FilterChip("+", false) { newList = true }
                }
            }
            if (!backgroundOk) {
                item(key = "bg-warning") { BackgroundWarning() }
            }
            downtime?.let { d ->
                item(key = "downtime") {
                    DowntimeBanner(d, onFix = { nav.push(Route.BackgroundSetup) }, onDismiss = { Uptime.dismiss(ctx); downtimeDismissed = true })
                }
            }
            if (org.locked.isNotEmpty() && !lock.hidden && query.isEmpty() && filter == "all") {
                item(key = "locked") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.push(Route.LockedChats) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(54.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Lock, null, tint = p.secondaryLabel, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Locked chats", style = Type.rowTitle.copy(fontWeight = FontWeight.Normal), color = p.label, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (history != null || slowConnect) {
                item(key = "status") { StatusBanner(history, conn) }
            }
            items(visible, key = { it.jid }) { chat ->
                ChatRow(
                    chat = chat,
                    typing = typing[chat.jid],
                    onClick = { nav.push(Route.Chat(chat.jid)) },
                    onLongClick = { onLongPress(chat) },
                )
            }
            if (visible.isEmpty() && history == null) {
                item(key = "empty") {
                    Text(
                        when {
                            query.isNotEmpty() -> "No chats match."
                            filter == "fav" -> "No favourites yet. Long-press a chat and choose Add to Favorites."
                            filter.startsWith("list:") -> "This list is empty. Long-press a chat and choose Add to list."
                            filter != "all" -> "No chats here."
                            else -> "No chats yet. Tap + to start one."
                        },
                        style = Type.subhead,
                        color = p.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                    )
                }
            }
            item(key = "e2e") {
                Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = p.secondaryLabel, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Your personal messages are end-to-end encrypted", style = Type.caption.copy(fontSize = 12.sp), color = p.secondaryLabel)
                }
            }
        }
        }

        TopEdgeFade(alpha = shade)
        TopControls(
            backdrop = backdrop,
            title = "Chats",
            showTitle = titleCollapsed,
            left = { GlassCircleButton(backdrop, Icons.Rounded.MoreHoriz, "More") { menuSheet = true } },
            right = {
                GlassCircleButton(backdrop, Icons.Outlined.PhotoCamera, "Camera (status)") { onCamera() }
                Spacer(Modifier.width(10.dp))
                GlassCircleButton(backdrop, Icons.Rounded.Add, "New chat", tint = Color.White, filled = p.accent) { nav.push(Route.NewChat) }
            },
        )
    }
    if (newList) NewListDialog(onDone = { id -> newList = false; if (id != null) filter = "list:$id" })
    if (menuSheet) {
        com.glasschat.ui.components.PopMenu(
            actions = listOf(
                SheetAction("Read all", Icons.Outlined.MarkChatRead) { Engine.markAllRead() },
                SheetAction("Archived chats", Icons.Outlined.Archive) { nav.push(Route.Archived) },
                SheetAction("Change background", Icons.Outlined.Wallpaper) { nav.push(Route.Wallpaper(Wallpapers.HOME)) },
            ),
            onDismiss = { menuSheet = false },
        )
    }
}

@Composable
fun TopControls(
    backdrop: Backdrop,
    title: String,
    showTitle: Boolean,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val a by animateFloatAsState(if (showTitle) 1f else 0f, label = "title")
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) { left() }
        Text(title, style = Type.title, color = p.label, modifier = Modifier.align(Alignment.Center).alpha(a))
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) { right() }
    }
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    focusRequester: FocusRequester? = null,
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(p.fill)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = Type.body, color = p.secondaryLabel)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Type.body.copy(color = p.label),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
    }
}

/** "Messages may not arrive while the app is closed" — tap to fix. */
@Composable
private fun BackgroundWarning() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.danger.copy(alpha = if (p.dark) 0.2f else 0.1f))
            .clickable {
                when {
                    !NotificationManagerCompat.from(ctx).areNotificationsEnabled() -> runCatching {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                        )
                    }
                    !OemSettings.batteryUnrestricted(ctx) -> OemSettings.requestUnrestrictedBattery(ctx)
                    else -> OemSettings.openAppInfo(ctx)
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.NotificationsOff, null, tint = p.danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Messages may not arrive while the app is closed", style = Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = p.label)
            Text(
                when {
                    !NotificationManagerCompat.from(ctx).areNotificationsEnabled() -> "Tap to turn on notifications"
                    !OemSettings.batteryUnrestricted(ctx) -> "Tap and choose Allow to let it run in the background"
                    else -> "Battery use is Restricted — tap, then Battery → Unrestricted"
                },
                style = Type.footnote,
                color = p.secondaryLabel,
            )
        }
    }
}

/** "Your phone closed the app for 44 min" — the real reason messages were late. */
@Composable
private fun DowntimeBanner(d: Downtime, onFix: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFF9500).copy(alpha = if (p.dark) 0.22f else 0.13f))
            .clickable(onClick = onFix)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (d.kind == "sleep") "Messages waited while the phone slept" else "Your phone stopped Whatsap in the background",
                style = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = p.label,
            )
            Text(
                "${Uptime.hhmm(d.from)}–${Uptime.hhmm(d.to)} (${d.minutes} min): messages couldn't arrive. Tap to fix it.",
                style = Type.footnote,
                color = p.secondaryLabel,
            )
        }
        Icon(
            Icons.Rounded.Close,
            "Dismiss",
            tint = p.secondaryLabel,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(10.dp),
        )
    }
}

/** Name a new chat list. Returns its id, or null when cancelled. */
@Composable
internal fun NewListDialog(onDone: (String?) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    com.glasschat.ui.glass.GlassModal(onDismiss = { onDone(null) }) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier.padding(horizontal = 24.dp).imePadding().fillMaxWidth().menuGlass(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .padding(18.dp),
        ) {
            Text("New list", style = Type.title, color = p.label)
            Text("e.g. Work, Family, Hotels. Add chats with a long-press → Add to list.", style = Type.footnote, color = p.secondaryLabel, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.fill).padding(horizontal = 12.dp, vertical = 11.dp)) {
                if (name.isEmpty()) Text("List name", style = Type.body, color = p.tertiaryLabel)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    singleLine = true,
                    textStyle = Type.body.copy(color = p.label),
                    cursorBrush = SolidColor(p.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                Text("Cancel", style = Type.body, color = p.link, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onDone(null) }.padding(10.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Create",
                    style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = if (name.isNotBlank()) p.link else p.tertiaryLabel,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = name.isNotBlank()) { onDone(ChatLists.createList(name, null).id) }.padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(history: Int?, conn: Engine.Conn) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.accent.copy(alpha = if (p.dark) 0.18f else 0.10f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = p.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                history != null -> "Syncing chats from your phone… ${history.coerceIn(0, 100)}%"
                else -> "Connecting…"
            },
            style = Type.subhead,
            color = p.label,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRow(chat: Chat, typing: Typing?, onClick: () -> Unit, onLongClick: () -> Unit, hasStatus: Boolean = false, onAvatar: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same rhythm as WhatsApp's list: ~79 dp rows, 50 dp photo, no dividers.
            Avatar(chat.jid, chat.isGroup, 50.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(top = 15.dp, bottom = 15.dp, end = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.name, style = Type.rowTitle, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (chat.lastTs > 0) {
                        Text(
                            Fmt.listStamp(ctx, chat.lastTs),
                            style = Type.subhead.copy(fontSize = 14.sp),
                            color = if (chat.unread > 0 && !chat.isMuted) p.accentText else p.secondaryLabel,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ChatPreview(chat, typing) }
                    Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (chat.isMuted) Icon(Icons.AutoMirrored.Outlined.VolumeOff, "Muted", tint = p.tertiaryLabel, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                        if (chat.pinned) Icon(Icons.Outlined.PushPin, "Pinned", tint = p.tertiaryLabel, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 35f })
                        if (chat.hasUnread) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier
                                    .height(21.dp)
                                    .widthIn(min = 21.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (chat.isMuted) p.tertiaryLabel else p.accent)
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (chat.unread > 0) chat.unread.toString() else "",
                                    style = Type.footnote.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPreview(chat: Chat, typing: Typing?) {
    val p = LocalPalette.current
    val lines = LocalChatStyle.current.previewLines.coerceIn(1, 2)
    if (typing != null) {
        val who = if (chat.isGroup) "${typing.sender} is " else ""
        Text("$who${if (typing.recording) "recording audio…" else "typing…"}", style = Type.subhead, color = p.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val draft = com.glasschat.ui.ChatDrafts.all[chat.jid]
    if (!draft.isNullOrBlank()) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF25D366))) { append("Draft: ") }
                append(draft.replace('\n', ' '))
            },
            style = Type.subhead,
            color = p.secondaryLabel,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val preview = previewFor(chat.lastType, chat.lastText, chat.lastRevoked)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (chat.lastFromMe && chat.lastId.isNotEmpty() && !chat.lastRevoked) {
            Ticks(chat.lastStatus, Modifier.padding(end = 3.dp), color = p.secondaryLabel, size = 17.dp)
        }
        Text(
            buildAnnotatedString {
                if (chat.isGroup && !chat.lastFromMe && chat.lastSender.isNotEmpty()) {
                    withStyle(SpanStyle(color = p.label.copy(alpha = 0.85f))) { append(chat.lastSender.removePrefix("~ ")); append(": ") }
                }
                if (chat.lastRevoked) withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(preview) } else append(preview)
            },
            style = Type.subhead,
            color = p.secondaryLabel,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// -----------------------------------------------------------------------------
// Long-press menu for a chat

@Composable
fun MuteSheet(jid: String, onDone: () -> Unit) {
    val now = System.currentTimeMillis()
    ActionSheet(
        title = "Other people won't see that you muted this chat.",
        actions = listOf(
            SheetAction("8 hours") { Engine.setMutedUntil(jid, now + 8 * 3_600_000L) },
            SheetAction("1 week") { Engine.setMutedUntil(jid, now + 7 * 86_400_000L) },
            SheetAction("Always") { Engine.setMutedUntil(jid, -1) },
        ),
        onDismiss = onDone,
    )
}

@Composable
fun MenuItem(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val color = if (destructive) p.danger else p.label
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.body, color = color, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun MenuDivider() {
    HorizontalDivider(color = LocalPalette.current.separator.copy(alpha = 0.6f), thickness = 0.5.dp)
}

// -----------------------------------------------------------------------------
// Archived chats

@Composable
fun ArchivedScreen(nav: Navigator) {
    val p = LocalPalette.current
    val backdrop = remember { Backdrop() }
    val chats by Engine.chats.collectAsState()
    val typing by Engine.typing.collectAsState()
    val archived = remember(chats) { chats.filter { it.archived } }
    var menuChat by remember { mutableStateOf<Chat?>(null) }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(p.background)) {
        LazyColumn(Modifier.fillMaxSize().backdropSource(backdrop).background(p.background), state = listState) {
            item {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(60.dp))
                Text(
                    "These chats stay archived when new messages arrive.",
                    style = Type.footnote,
                    color = p.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            items(archived, key = { it.jid }) { c ->
                ChatRow(c, typing[c.jid], onClick = { nav.push(Route.Chat(c.jid)) }, onLongClick = { menuChat = c })
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
        TopEdgeFade()
        TopControls(
            backdrop,
            title = "Archived",
            showTitle = true,
            left = { GlassCircleButton(backdrop, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back", modifier = Modifier) { nav.pop() } },
            right = {},
        )
        menuChat?.let { ChatContextMenu(it, onDismiss = { menuChat = null }, onMessage = {}) }
    }
}

@Composable
fun ListIsScrolled(state: LazyListState): Boolean {
    val s by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 } }
    return s
}


/** "Send to…": pick a chat (or your status) for something from the camera. */
@Composable
fun SendToPicker(onPick: (Chat) -> Unit, onStatus: (() -> Unit)?, onClose: () -> Unit, previewText: String = "Photo") {
    // The WhatsApp-style "Send to" sheet; the first chosen chat opens the send preview.
    SendToSheet(
        previewText = previewText,
        allowStatus = onStatus != null,
        onClose = onClose,
        onSend = { targets, toStatus ->
            val first = targets.firstOrNull()?.let { t -> Engine.chats.value.find { it.jid == t.jid } }
            when {
                toStatus && first == null -> onStatus?.invoke()
                first != null -> onPick(first)
            }
        },
    )
}
