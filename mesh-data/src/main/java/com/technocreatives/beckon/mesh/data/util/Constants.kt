package com.technocreatives.beckon.mesh.data.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

object Constants {
    // no.nordicsemi.android.mesh.utils.MeshAddress.UNASSIGNED_ADDRESS
    const val UnassignedAddress: Int = 0x0000

    // no.nordicsemi.android.mesh.models.SigModelParser.CONFIGURATION_CLIENT;
    const val CONFIGURATION_CLIENT: Short = 0x0001
}

object MeshParserUtils {

    fun bytesToInt(b: ByteArray): Int {
        return if (b.size == 4) ByteBuffer.wrap(b)
            .order(ByteOrder.BIG_ENDIAN).int else ByteBuffer.wrap(
            b
        ).order(
            ByteOrder.BIG_ENDIAN
        ).short
            .toInt()
    }

    fun hexToInt(hex: String): Int {
        return bytesToInt(toByteArray(hex))
    }

    fun toByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val bytes = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            bytes[i / 2] = ((Character.digit(hexString[i], 16) shl 4)
                    + Character.digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return bytes
    }

    fun formatUuid(uuidHex: String): String? {
        return if (isUuidPattern(uuidHex)) {
            StringBuffer(uuidHex)
                .insert(8, "-")
                .insert(13, "-")
                .insert(18, "-")
                .insert(23, "-")
                .toString()
                .uppercase(Locale.US)
        } else uuidHex
    }

    private val PATTERN_UUID_HEX = "[0-9a-fA-F]{32}".toRegex()

    fun isUuidPattern(uuidHex: String): Boolean {
        return uuidHex.matches(PATTERN_UUID_HEX)
    }

    fun formatCompanyIdentifier(companyIdentifier: Int, add0x: Boolean): String {
        return if (add0x) "0x" + String.format(
            Locale.US,
            "%04X",
            companyIdentifier
        ) else String.format(
            Locale.US, "%04X", companyIdentifier
        )
    }
}
