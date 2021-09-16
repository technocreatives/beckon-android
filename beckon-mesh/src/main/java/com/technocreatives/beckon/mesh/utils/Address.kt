package com.technocreatives.beckon.mesh.utils

import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.data.PublishableAddress
import com.technocreatives.beckon.mesh.data.UnicastAddress
import no.nordicsemi.android.mesh.utils.MeshAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.toPublishableAddress(): PublishableAddress {
    val value = ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).int
    return when {
        MeshAddress.isValidGroupAddress(value) -> GroupAddress(value)
        MeshAddress.isValidUnicastAddress(value) -> UnicastAddress(value)
        else -> throw IllegalArgumentException("Not supported $this")
    }
}