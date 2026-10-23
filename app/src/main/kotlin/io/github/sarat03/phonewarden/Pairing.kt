package io.github.sarat03.phonewarden

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.sarat03.phonewarden.tls.AdbKey
import io.github.sarat03.phonewarden.tls.TlsPairingTransport
import java.net.InetAddress
import java.security.interfaces.RSAPublicKey
import kotlin.concurrent.thread
import phonewarden.adb.AdbPublicKey
import phonewarden.adb.pairing.PairingFlow
import phonewarden.adb.pairing.PeerInfo
import phonewarden.adb.pairing.WrongCodeException

/**
 * Pairing without leaving the Wireless debugging screen: the code is typed into a notification
 * reply while Android shows it, and the pairing port is found by mDNS.
 */
object Pairing {
    private const val CHANNEL = "pairing"
    private const val ID = 1
    private const val KEY_CODE = "code"
    private val CODE = Regex("^[0-9]{6}$")

    fun isPaired(ctx: Context) = prefs(ctx).getBoolean("paired", false)

    /** Back to the pairing screen; the next pairing replaces this phone's trust in our key. */
    fun forget(ctx: Context) = prefs(ctx).edit().putBoolean("paired", false).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pairing", Context.MODE_PRIVATE)

    /** Shows the notification with a code field. [text] explains the next step. */
    fun prompt(ctx: Context, text: String = "In Wireless debugging, tap \"Pair device with pairing code\", then type the 6-digit code here.") {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pairing", NotificationManager.IMPORTANCE_HIGH))
        val reply = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, PairReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // mutable so Android can add the typed code
        )
        val input = RemoteInput.Builder(KEY_CODE).setLabel("6-digit pairing code").build()
        val action = Notification.Action.Builder(null, "Enter code", reply).addRemoteInput(input).build()
        nm.notify(ID, Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Pair PhoneWarden")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .addAction(action)
            .setOngoing(true)
            .build())
    }

    private fun done(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.notify(ID, Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("PhoneWarden is paired").setContentText(text).setAutoCancel(true).build())
    }

    /** Pairs with this phone's own adbd. Throws with a message fit for the notification. */
    fun pair(ctx: Context, code: String) {
        require(CODE.matches(code)) { "The code is 6 digits. Try again." }
        val ports = Mdns.ports(ctx, Mdns.PAIRING)
        if (ports.isEmpty()) throw IllegalStateException("Could not find the pairing dialog. Keep it open on screen and try again.")
        val key = AdbKey.getOrCreate()
        val pub = key.certificate.publicKey as RSAPublicKey
        val ours = PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY, AdbPublicKey.encode(pub.modulus, pub.publicExponent, "phonewarden@${Build.MODEL.filter { it.isLetterOrDigit() }}").toByteArray())
        var last: Exception? = null
        for (port in ports) {
            try {
                // loopback only: a pairing service advertised by another phone simply fails here
                TlsPairingTransport.connect(InetAddress.getLoopbackAddress(), port, key).use { PairingFlow(it, code, ours).run() }
                prefs(ctx).edit().putBoolean("paired", true).apply()
                return
            } catch (e: WrongCodeException) {
                throw IllegalStateException("That code did not match. Check the code on screen and try again.")
            } catch (e: Exception) {
                last = e
            }
        }
        throw IllegalStateException("Pairing failed (${last?.message}). Try again.")
    }

    class PairReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString()?.trim().orEmpty()
            val pending = goAsync()
            thread {
                try {
                    pair(ctx, code)
                    done(ctx, "Go back to PhoneWarden to check this phone.")
                } catch (e: Exception) {
                    prompt(ctx, e.message ?: "Pairing failed. Try again.")
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
