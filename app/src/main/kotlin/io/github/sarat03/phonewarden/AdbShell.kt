package io.github.sarat03.phonewarden

import phonewarden.adb.AdbConnection
import phonewarden.engine.Shell
import phonewarden.engine.commandLine

/** Engine commands over our own ADB connection; a non-zero exit is an error, like subprocess check=True. */
fun AdbConnection.asShell() = Shell { args ->
    val r = shellV2(commandLine(args))
    if (r.exitCode != 0) throw java.io.IOException("${args.first()} exited ${r.exitCode}: ${r.stderr.trim()}")
    r.stdout
}
