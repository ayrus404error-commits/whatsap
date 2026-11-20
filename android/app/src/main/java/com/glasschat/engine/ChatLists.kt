package com.glasschat.engine

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A custom chat list ("Work", "Family"…), shown as a filter on the chats screen. */
@Immutable
data class ChatList(val id: String, val name: String, val chats: Set<String>)

/** Favourites, locked chats and custom lists. Kept on this phone. */
@Immutable
data class ChatOrganizer(
    val favorites: Set<String> = emptySet(),
    val locked: Set<String> = emptySet(),
    val lists: List<ChatList> = emptyList(),
)

object ChatLists {
    private val _state = MutableStateFlow(ChatOrganizer())
    val state = _state.asStateFlow()
    private var app: Context? = null

    private fun sp(ctx: Context) = ctx.getSharedPreferences("chat_lists", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        val p = sp(ctx)
        val lists = runCatching {
            val a = JSONArray(p.getString("lists", "[]"))
            List(a.length()) {
                val o = a.getJSONObject(it)
                val c = o.optJSONArray("chats") ?: JSONArray()
                ChatList(o.optString("id"), o.optString("name"), List(c.length()) { i -> c.optString(i) }.toSet())
            }
        }.getOrDefault(emptyList())
        _state.value = ChatOrganizer(
            favorites = p.getStringSet("favorites", emptySet()).orEmpty().toSet(),
            locked = p.getStringSet("locked", emptySet()).orEmpty().toSet(),
            lists = lists,
        )
    }

    private fun save(s: ChatOrganizer) {
        _state.value = s
        val ctx = app ?: return
        val a = JSONArray()
        s.lists.forEach { l -> a.put(JSONObject().put("id", l.id).put("name", l.name).put("chats", JSONArray(l.chats.toList()))) }
        sp(ctx).edit()
            .putStringSet("favorites", s.favorites)
            .putStringSet("locked", s.locked)
            .putString("lists", a.toString())
            .apply()
    }

    fun isLocked(chat: String) = chat in _state.value.locked

    fun setFavorite(chat: String, on: Boolean) {
        val s = _state.value
        save(s.copy(favorites = if (on) s.favorites + chat else s.favorites - chat))
    }

    fun setLocked(chat: String, on: Boolean) {
        val s = _state.value
        save(s.copy(locked = if (on) s.locked + chat else s.locked - chat))
    }

    fun createList(name: String, firstChat: String?): ChatList {
        val l = ChatList("l" + System.currentTimeMillis(), name.trim().take(30), setOfNotNull(firstChat))
        val s = _state.value
        save(s.copy(lists = s.lists + l))
        return l
    }

    fun setInList(listId: String, chat: String, on: Boolean) {
        val s = _state.value
        save(s.copy(lists = s.lists.map { l -> if (l.id != listId) l else l.copy(chats = if (on) l.chats + chat else l.chats - chat) }))
    }

    fun renameList(listId: String, name: String) {
        val s = _state.value
        save(s.copy(lists = s.lists.map { if (it.id == listId) it.copy(name = name.trim().take(30)) else it }))
    }

    fun deleteList(listId: String) {
        val s = _state.value
        save(s.copy(lists = s.lists.filter { it.id != listId }))
    }

    /** A deleted chat leaves every list. */
    fun forget(chat: String) {
        val s = _state.value
        save(
            s.copy(
                favorites = s.favorites - chat,
                locked = s.locked - chat,
                lists = s.lists.map { it.copy(chats = it.chats - chat) },
            ),
        )
    }
}
