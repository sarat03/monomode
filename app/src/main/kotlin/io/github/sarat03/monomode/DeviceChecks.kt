package io.github.sarat03.monomode

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.security.KeyStore
import java.security.cert.X509Certificate

/** Exposure checks the app can answer itself, without adb: the shell cannot read these, or only via fragile dumpsys text. */
object DeviceChecks {
    /** Packages with active device admin rights. */
    fun deviceAdmins(ctx: Context): List<String> =
        ctx.getSystemService(DevicePolicyManager::class.java).activeAdmins.orEmpty().map { it.packageName }.distinct()

    /** Subjects of CA certificates the user (or an app with their help) installed; these can read encrypted traffic. */
    fun userCertificates(): List<String> {
        val ks = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        return ks.aliases().toList().filter { it.startsWith("user:") }
            .map { (ks.getCertificate(it) as X509Certificate).subjectX500Principal.name }
    }

    fun vpnActive(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}
