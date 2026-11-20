package com.glasschat.engine

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class Chat(
    val jid: String,
    val name: String,
    val phone: String,
    val isGroup: Boolean,
    val unread: Int,
    val markedUnread: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val mutedUntil: Long,
    val lastTs: Long,
    val lastId: String,
    val lastType: String,
    val lastText: String,
    val lastFromMe: Boolean,
    val lastStatus: Int,
    val lastSender: String,
    val lastRevoked: Boolean,
    val participants: Int,
    /** You blocked this person. */
    val blocked: Boolean = false,
    /** Disappearing-messages timer in seconds (0 = off). */
    val ephemeral: Int = 0,
    /** In your phone's contacts (people only). */
    val saved: Boolean = true,
) {
    val isMuted: Boolean get() = mutedUntil < 0 || mutedUntil > System.currentTimeMillis()
    val hasUnread: Boolean get() = unread > 0 || markedUnread

    companion object {
        fun from(o: JSONObject) = Chat(
            jid = o.optString("jid"),
            name = o.optString("name"),
            phone = o.optString("phone"),
            isGroup = o.optBoolean("isGroup"),
            unread = o.optInt("unread"),
            markedUnread = o.optBoolean("markedUnread"),
            pinned = o.optBoolean("pinned"),
            archived = o.optBoolean("archived"),
            mutedUntil = o.optLong("mutedUntil"),
            lastTs = o.optLong("lastTs"),
            lastId = o.optString("lastId"),
            lastType = o.optString("lastType"),
            lastText = o.optString("lastText"),
            lastFromMe = o.optBoolean("lastFromMe"),
            lastStatus = o.optInt("lastStatus"),
            lastSender = o.optString("lastSender"),
            lastRevoked = o.optBoolean("lastRevoked"),
            participants = o.optInt("participants"),
            blocked = o.optBoolean("blocked"),
            ephemeral = o.optInt("ephemeral"),
            saved = o.optBoolean("saved", true),
        )

        fun list(json: String): List<Chat> = runCatching {
            val a = JSONArray(json)
            List(a.length()) { from(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

@Immutable
data class Reaction(val emoji: String, val count: Int, val mine: Boolean)

@Immutable
data class PollOption(val name: String, val votes: Int, val mine: Boolean)

@Immutable
data class PollState(val options: List<PollOption>, val voters: Int, val multi: Boolean) {
    companion object {
        fun from(o: JSONObject?): PollState? {
            o ?: return null
            val opts = o.optJSONArray("options") ?: return null
            return PollState(
                options = List(opts.length()) {
                    val x = opts.getJSONObject(it)
                    PollOption(x.optString("name"), x.optInt("votes"), x.optBoolean("mine"))
                },
                voters = o.optInt("voters"),
                multi = o.optBoolean("multi"),
            )
        }
    }
}

@Immutable
data class Message(
    val chat: String,
    val id: String,
    val sender: String,
    val senderName: String,
    val fromMe: Boolean,
    val ts: Long,
    val type: String,
    val text: String,
    val extra: JSONObject?,
    val status: Int,
    val quotedId: String,
    val quotedName: String,
    val quotedText: String,
    val quotedType: String,
    val quotedSender: String,
    val edited: Boolean,
    val revoked: Boolean,
    val reactions: List<Reaction>,
    /** File on this device (downloaded or sent), or "". */
    val local: String = "",
    val poll: PollState? = null,
    val starred: Boolean = false,
    /** Kept in a chat with disappearing messages. */
    val kept: Boolean = false,
    /** "@Name" mentions in the text; me = it's you. */
    val mentions: List<Mention> = emptyList(),
) {
    val forwarded: Boolean get() = extra?.optBoolean("forwarded") == true

    companion object {
        const val FAILED = -1
        const val PENDING = 0
        const val SENT = 1
        const val DELIVERED = 2
        const val READ = 3
        const val PLAYED = 4

        fun from(o: JSONObject): Message {
            val extraStr = o.optString("extra")
            val reacts = o.optJSONArray("reactions")
            return Message(
                chat = o.optString("chat"),
                id = o.optString("id"),
                sender = o.optString("sender"),
                senderName = o.optString("senderName"),
                fromMe = o.optBoolean("fromMe"),
                ts = o.optLong("ts"),
                type = o.optString("type"),
                text = o.optString("text"),
                extra = if (extraStr.isNullOrEmpty()) null else runCatching { JSONObject(extraStr) }.getOrNull(),
                status = o.optInt("status"),
                quotedId = o.optString("quotedId"),
                quotedName = o.optString("quotedName"),
                quotedText = o.optString("quotedText"),
                quotedType = o.optString("quotedType"),
                quotedSender = o.optString("quotedSender"),
                edited = o.optBoolean("edited"),
                revoked = o.optBoolean("revoked"),
                reactions = if (reacts == null) emptyList() else List(reacts.length()) {
                    val r = reacts.getJSONObject(it)
                    Reaction(r.optString("emoji"), r.optInt("count"), r.optBoolean("mine"))
                },
                local = o.optString("local"),
                poll = PollState.from(o.optJSONObject("poll")),
                starred = o.optBoolean("starred"),
                kept = o.optBoolean("kept"),
                mentions = runCatching {
                    val a = org.json.JSONArray(o.optString("mentions").ifBlank { "[]" })
                    List(a.length()) { i -> a.getJSONObject(i).let { Mention(it.optString("t"), it.optBoolean("me")) } }
                }.getOrDefault(emptyList()),
            )
        }

        fun list(json: String): List<Message> = runCatching {
            val a = JSONArray(json)
            List(a.length()) { from(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

@Immutable
data class Contact(val jid: String, val name: String, val phone: String)

@Immutable
data class StatusItem(
    val id: String,
    val type: String,
    val text: String,
    val extra: JSONObject?,
    val ts: Long,
    val viewed: Boolean,
    val local: String,
    val status: Int,
    val views: Int,
)

@Immutable
data class StatusGroup(
    val sender: String,
    val name: String,
    val mine: Boolean,
    val lastTs: Long,
    val unviewed: Int,
    val items: List<StatusItem>,
) {
    companion object {
        fun list(json: String): List<StatusGroup> = runCatching {
            val a = JSONArray(json)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val its = o.optJSONArray("items") ?: JSONArray()
                StatusGroup(
                    sender = o.optString("sender"),
                    name = o.optString("name"),
                    mine = o.optBoolean("mine"),
                    lastTs = o.optLong("lastTs"),
                    unviewed = o.optInt("unviewed"),
                    items = List(its.length()) { j ->
                        val t = its.getJSONObject(j)
                        val ex = t.optString("extra")
                        StatusItem(
                            id = t.optString("id"),
                            type = t.optString("type"),
                            text = t.optString("text"),
                            extra = if (ex.isNullOrEmpty()) null else runCatching { JSONObject(ex) }.getOrNull(),
                            ts = t.optLong("ts"),
                            viewed = t.optBoolean("viewed"),
                            local = t.optString("local"),
                            status = t.optInt("status"),
                            views = t.optInt("views"),
                        )
                    },
                )
            }
        }.getOrDefault(emptyList())
    }
}

@Immutable
data class Sticker(val id: String, val animated: Boolean, val fav: Boolean) {
    companion object {
        fun list(json: String): List<Sticker> = runCatching {
            val a = JSONArray(json)
            List(a.length()) {
                val o = a.getJSONObject(it)
                Sticker(o.optString("id"), o.optBoolean("animated"), o.optBoolean("fav"))
            }
        }.getOrDefault(emptyList())
    }
}

data class Typing(val chat: String, val sender: String, val recording: Boolean, val at: Long)

data class Presence(val online: Boolean, val lastSeen: Long)

/** Short preview text for a message type, as WhatsApp shows in lists. */
fun previewFor(type: String, text: String, revoked: Boolean = false): String {
    if (revoked) return "This message was deleted"
    return when (type) {
        "text" -> text
        "image" -> if (text.isNotBlank()) "📷 $text" else "📷 Photo"
        "video" -> if (text.isNotBlank()) "🎥 $text" else "🎥 Video"
        "gif" -> "GIF"
        "voice" -> "🎤 Voice message"
        "audio" -> "🎵 Audio"
        "document" -> "📄 " + text.ifBlank { "Document" }
        "sticker" -> "Sticker"
        "location" -> "📍 " + text.ifBlank { "Location" }
        "contact" -> "👤 " + text.ifBlank { "Contact" }
        "poll" -> "📊 " + text.ifBlank { "Poll" }
        "event" -> "📅 " + text.ifBlank { "Event" }
        "invite" -> "Group invite: $text"
        "call" -> "📞 Call"
        "waiting" -> "Waiting for this message…"
        else -> text.ifBlank { "Message" }
    }
}

/** A member of a group, named as your phone would show them. */
@Immutable
data class GroupMember(
    val jid: String,
    val name: String,
    val phone: String,
    val admin: Boolean,
    val isMe: Boolean,
    val saved: Boolean,
)

/** A group's details and members. */
@Immutable
data class GroupInfo(
    val jid: String,
    val name: String,
    val topic: String,
    val created: Long,
    val createdBy: String,
    val members: List<GroupMember>,
) {
    companion object {
        fun from(o: JSONObject): GroupInfo {
            val a = o.optJSONArray("participants")
            val members = if (a == null) emptyList() else List(a.length()) { i ->
                val p = a.getJSONObject(i)
                GroupMember(
                    jid = p.optString("jid"),
                    name = p.optString("name"),
                    phone = p.optString("phone"),
                    admin = p.optBoolean("admin"),
                    isMe = p.optBoolean("isMe"),
                    saved = p.optBoolean("saved"),
                )
            }
            return GroupInfo(
                jid = o.optString("jid"),
                name = o.optString("name"),
                topic = o.optString("topic"),
                created = o.optLong("created"),
                createdBy = o.optString("createdBy"),
                members = members,
            )
        }
    }
}


/** One person's receipt for a message you sent. */
@Immutable
data class ReceiptEntry(val jid: String, val name: String, val ts: Long)

/** Who received, read or played one of your messages, and when. */
@Immutable
data class MessageInfo(
    val sent: Long,
    val status: Int,
    val read: List<ReceiptEntry>,
    val played: List<ReceiptEntry>,
    val delivered: List<ReceiptEntry>,
) {
    companion object {
        private fun entries(a: JSONArray?): List<ReceiptEntry> =
            if (a == null) emptyList() else List(a.length()) {
                val o = a.getJSONObject(it)
                ReceiptEntry(o.optString("jid"), o.optString("name"), o.optLong("ts"))
            }

        fun from(o: JSONObject) = MessageInfo(
            sent = o.optLong("sent"),
            status = o.optInt("status"),
            read = entries(o.optJSONArray("read")),
            played = entries(o.optJSONArray("played")),
            delivered = entries(o.optJSONArray("delivered")),
        )
    }
}

/** One @mention inside a message's text. */
@Immutable
data class Mention(val text: String, val me: Boolean)
