package io.github.sarat03.monomode

import monomode.adb.AdbConnection
import monomode.engine.Shell
import monomode.engine.commandLine

/** Engine commands over our own ADB connection; a non-zero exit is an error, like subprocess check=True. */
fun AdbConnection.asShell() = Shell { args ->
    val r = shellV2(commandLine(args))
    // the whole command and whatever it printed: some tools (cmd) report errors on stdout, not stderr
    if (r.exitCode != 0) throw java.io.IOException("${commandLine(args)} exited ${r.exitCode}: ${(r.stderr + r.stdout).trim()}")
    r.stdout
}
