package com.oavi.browser.model

/**
 * Addresses on the user's own network (routers, printers, NAS boxes). From Android 17 an app
 * needs its own permission before it may connect to them.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /**
     * True for hosts that are on the local network by their form alone: private and
     * link-local IP addresses, and `.local` names. Loopback (localhost, 127.0.0.1) is not
     * the network and needs no permission.
     */
    fun isLocalHost(host: String): Boolean {
        val name = host.trim().lowercase().removePrefix("[").removeSuffix("]").trimEnd('.')
        if (name.isEmpty()) return false
        if (name.endsWith(".local")) return true
        parseIpv4(name)?.let { return isLocalIpv4(it) }
        if (name.contains(':')) return parseIpv6(name)?.let(::isLocalIpv6) ?: false
        return false
    }

    /** The same test for an address already resolved, as raw bytes (4 for IPv4, 16 for IPv6). */
    fun isLocalAddress(bytes: ByteArray): Boolean {
        val unsigned = bytes.map { it.toInt() and 0xFF }
        return when (unsigned.size) {
            4 -> isLocalIpv4(unsigned)
            16 -> isLocalIpv6(unsigned)
            else -> false
        }
    }

    private fun isLocalIpv4(b: List<Int>): Boolean = when {
        b[0] == 10 -> true
        b[0] == 172 && b[1] in 16..31 -> true
        b[0] == 192 && b[1] == 168 -> true
        b[0] == 169 && b[1] == 254 -> true
        else -> false
    }

    private fun isLocalIpv6(b: List<Int>): Boolean = when {
        (b[0] and 0xFE) == 0xFC -> true // unique local, fc00::/7
        b[0] == 0xFE && (b[1] and 0xC0) == 0x80 -> true // link-local, fe80::/10
        b.subList(0, 10).all { it == 0 } && b[10] == 0xFF && b[11] == 0xFF -> isLocalIpv4(b.subList(12, 16))
        else -> false
    }

    private fun parseIpv4(text: String): List<Int>? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val values = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            part.toInt().takeIf { it <= 255 } ?: return null
        }
        return values
    }

    /** Parses an IPv6 literal (with `::`, an embedded IPv4 tail or a `%zone`) into 16 bytes. */
    private fun parseIpv6(text: String): List<Int>? {
        val address = text.substringBefore('%')
        if (address.count { it == ':' } < 2) return null
        val compressed = address.indexOf("::")
        if (compressed >= 0 && address.indexOf("::", compressed + 1) >= 0) return null
        val head = if (compressed >= 0) address.substring(0, compressed) else address
        val tail = if (compressed >= 0) address.substring(compressed + 2) else ""

        fun groups(part: String): List<Int>? {
            if (part.isEmpty()) return emptyList()
            val pieces = part.split(':')
            val result = ArrayList<Int>()
            for ((index, piece) in pieces.withIndex()) {
                if (index == pieces.lastIndex && piece.contains('.')) {
                    val v4 = parseIpv4(piece) ?: return null
                    result += (v4[0] shl 8) or v4[1]
                    result += (v4[2] shl 8) or v4[3]
                } else {
                    if (piece.isEmpty() || piece.length > 4) return null
                    result += piece.toIntOrNull(16) ?: return null
                }
            }
            return result
        }

        val first = groups(head) ?: return null
        val last = groups(tail) ?: return null
        val missing = 8 - first.size - last.size
        if (if (compressed >= 0) missing < 1 else missing != 0) return null
        return (first + List(missing) { 0 } + last).flatMap { listOf(it shr 8, it and 0xFF) }
    }
}
