package phonewarden.engine

/** What the app reads itself (DeviceChecks), handed to the engine as plain data. */
data class DeviceFacts(val deviceAdmins: List<String>, val userCertificates: List<String>, val vpnActive: Boolean)

/** LOOK: worth a look by the owner. INFO: context. Never a verdict. */
enum class Level { LOOK, INFO }

data class Finding(val level: Level, val title: String, val detail: String)

/**
 * Plain-language results. States what is there and what it allows; never calls the phone
 * hacked or safe, because a check like this cannot know either.
 * [ownAdbPort] is the Wireless debugging port of this very check, which PhoneWarden switches off afterwards.
 */
fun findings(net: NetworkReport, access: AccessReport, dev: DeviceFacts, ownAdbPort: Int): List<Finding> {
    val out = mutableListOf<Finding>()
    fun look(title: String, detail: String) { out += Finding(Level.LOOK, title, detail) }

    for (s in net.listening.filter { it.port != ownAdbPort }) {
        if (s.port == 5555 || s.uid == 2000)
            look("Debugging is open to the network", "Port ${s.port} accepts debugging connections from other devices on this network. Turn off USB and wireless debugging in Developer options unless you use them.")
        else
            look("${s.app} accepts connections", "It listens on port ${s.port}, so devices on the same network can connect to it.")
    }
    for (s in net.inbound.filter { it.port != ownAdbPort })
        look("Something is connected to ${s.app}", "${s.remote} is connected to it right now.")
    for (pkg in access.accessibility)
        look("$pkg can see and control your screen", "It has accessibility access: it can read what is on screen and tap for you. Fine for tools you chose; remove it in Settings > Accessibility if you did not.")
    for (pkg in access.notificationAccess)
        look("$pkg can read all your notifications", "Including message previews and login codes. Manage in Settings > Notifications > Device & app notifications.")
    for ((pkg, installer) in access.outsidePlayStore)
        look("$pkg did not come from the Play Store", if (installer == null) "It was installed from a file or an unknown source." else "It was installed by $installer.")
    for (pkg in dev.deviceAdmins)
        look("$pkg is a device admin", "It can lock the phone or erase it. Manage in Settings > Security > Device admin apps.")
    for (subject in dev.userCertificates)
        look("A certificate you or an app added is trusted", "$subject. A certificate like this can let its owner read encrypted traffic. Manage in Settings > Security > Encryption & credentials.")
    if (dev.vpnActive)
        look("A VPN is on", "All traffic goes through the VPN app. Fine if you set it up; otherwise check Settings > Network > VPN.")

    // built-in apps (Play services, keyboard, system uids) always talk to the internet; listing them is noise
    fun builtIn(app: String) = app.startsWith("system(") || app.split(",").all { it in access.system }
    for ((app, conns) in net.outbound.filterNot { builtIn(it.app) }.groupBy { it.app })
        out += Finding(Level.INFO, "$app is talking to the internet", "${conns.size} open connection${if (conns.size == 1) "" else "s"}: ${conns.joinToString { it.remote }}.")
    if (out.isEmpty())
        out += Finding(Level.INFO, "Nothing stood out", "None of the things this check looks for turned up. It looks at network ports, special app access, certificates, VPN and where apps came from.")
    return out
}
