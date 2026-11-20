package com.glasschat.engine

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/** Chat lock settings, like WhatsApp's: a secret code, and hiding locked chats. */
data class LockSettings(val hasCode: Boolean = false, val hide: Boolean = false) {
    /** Locked chats are hidden from the chat list (only possible with a secret code). */
    val hidden: Boolean get() = hasCode && hide
}

/**
 * Locked chats open with a secret code (when one is set) or the phone's own
 * fingerprint / face / PIN check. Once unlocked they stay open until you've
 * been away from the app for a while.
 */
object ChatLock {
    private val _settings = MutableStateFlow(LockSettings())
    val settings = _settings.asStateFlow()

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("chat_lock", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        val p = sp(ctx)
        _settings.value = LockSettings(hasCode = p.getString("code_hash", null) != null, hide = p.getBoolean("hide", false))
    }

    private fun hash(salt: String, code: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + ":" + code.trim()).toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Sets (or with null, removes) the secret code. Stored only as a salted hash. */
    fun setCode(ctx: Context, code: String?) {
        val e = sp(ctx).edit()
        if (code == null) {
            e.remove("code_hash").remove("code_salt").putBoolean("hide", false)
        } else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            e.putString("code_salt", salt).putString("code_hash", hash(salt, code))
        }
        e.apply()
        init(ctx)
    }

    /** True (and unlocks) when [code] is the secret code. */
    fun checkCode(ctx: Context, code: String): Boolean {
        val p = sp(ctx)
        val salt = p.getString("code_salt", null) ?: return false
        val stored = p.getString("code_hash", null) ?: return false
        val ok = code.isNotBlank() && MessageDigest.isEqual(hash(salt, code).toByteArray(), stored.toByteArray())
        if (ok) unlocked = true
        return ok
    }

    fun setHide(ctx: Context, hide: Boolean) {
        sp(ctx).edit().putBoolean("hide", hide).apply()
        init(ctx)
    }

    /** Bumped whenever chats lock again, so open locked chats close their gate. */
    private val _epoch = MutableStateFlow(0)
    val epoch = _epoch.asStateFlow()

    fun lockNow() {
        unlocked = false
        _epoch.value = _epoch.value + 1
    }

    /** Away from the app longer than this locks chats again. */
    const val RELOCK_MS = 5 * 60_000L

    @Volatile var unlocked = false
    @Volatile private var leftAt = 0L

    fun onForeground(fg: Boolean) {
        val now = System.currentTimeMillis()
        if (!fg) {
            leftAt = now
        } else if (leftAt > 0 && now - leftAt > RELOCK_MS) {
            lockNow()
        }
    }

    /** Locking needs a screen lock on the phone. */
    fun canLock(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun authenticate(ctx: Context, title: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (unlocked) {
            onSuccess()
            return
        }
        if (!canLock(ctx)) {
            onFailure("Set a screen lock (PIN, pattern or fingerprint) on your phone to use locked chats.")
            return
        }
        try {
            val b = BiometricPrompt.Builder(ctx)
                .setTitle(title)
                .setSubtitle("Use your fingerprint, face or phone PIN")
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION")
                b.setDeviceCredentialAllowed(true)
            }
            b.build().authenticate(
                CancellationSignal(),
                ctx.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        unlocked = true
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        onFailure(errString?.toString() ?: "Couldn't unlock")
                    }
                },
            )
        } catch (t: Throwable) {
            onFailure(t.message ?: "Couldn't unlock")
        }
    }
}
