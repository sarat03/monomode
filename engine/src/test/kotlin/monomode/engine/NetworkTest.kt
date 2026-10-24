package monomode.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ported from test_core.py; /proc/net fixtures in the kernel's format. */
class NetworkTest {
    private val tcp4 = """
  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000  2000        0 1 1
   1: 00000000:15B3 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10050        0 2 1
   2: 0F01A8C0:15B3 2301A8C0:D431 01 00000000:00000000 00:00000000 00000000 10050        0 3 1
   3: 0F01A8C0:9C40 22D83AD8:01BB 01 00000000:00000000 00:00000000 00000000 10077        0 4 1
""".trimStart('\n')
    private val tcp6 = """
  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
   0: 0000000000000000FFFF00000F01A8C0:15B3 0000000000000000FFFF00006401A8C0:C350 01 00000000:00000000 00:00000000 00000000 10050        0 5 1
""".trimStart('\n')

    private val calls = mutableListOf<List<String>>()
    private val sh = Shell { args ->
        calls += args
        when (args) {
            listOf("cat", "/proc/net/tcp") -> tcp4
            listOf("cat", "/proc/net/tcp6") -> tcp6
            listOf("pm", "list", "packages", "-U") -> "package:com.share.app uid:10050\npackage:com.chat.app uid:10077\n"
            else -> throw AssertionError("unexpected command $args")
        }
    }
    private val r = network(sh)

    @Test fun `listening ignores loopback`() = assertEquals(listOf("0.0.0.0:5555" to "com.share.app"), r.listening.map { it.local to it.app })

    @Test fun `inbound is a connection to a listening port`() =
        assertEquals(setOf("192.168.1.35:54321" to "com.share.app", "192.168.1.100:50000" to "com.share.app"), r.inbound.map { it.remote to it.app }.toSet())

    @Test fun `outbound lists remote and app`() = assertEquals(listOf("216.58.216.34:443" to "com.chat.app"), r.outbound.map { it.remote to it.app })


    @Test fun `reads only fixed proc files`() =
        assertEquals(setOf(listOf("cat", "/proc/net/tcp"), listOf("cat", "/proc/net/tcp6"), listOf("pm", "list", "packages", "-U")), calls.toSet())
}
