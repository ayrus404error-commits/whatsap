package com.glasschat.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.glasschat.engine.Engine

/**
 * After you add someone to your phone's contacts from this app, reads the
 * name you saved and shows it at once. (Otherwise the name only arrives once
 * WhatsApp on your phone syncs its contacts, which can take hours.)
 */
object ContactBook {
    /** The number we sent to the "new contact" screen and are waiting on. */
    @Volatile var pending: String? = null

    fun canRead(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Name saved in the phone's contacts for this number, or null. */
    fun nameFor(ctx: Context, phone: String): String? {
        if (!canRead(ctx)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        return runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    /** Called when the app comes back to the front. */
    fun onResume(activity: Activity) {
        val phone = pending ?: return
        if (!canRead(activity)) {
            activity.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST)
            return
        }
        val name = nameFor(activity, phone) ?: return
        pending = null
        Engine.setContactName(phone, name)
    }

    const val REQUEST = 4711
}
