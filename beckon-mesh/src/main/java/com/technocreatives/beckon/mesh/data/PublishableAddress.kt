package com.technocreatives.beckon.mesh.data

import no.nordicsemi.android.mesh.utils.AddressArray

sealed interface PublishableAddress

fun PublishableAddress.value(): Int = when (this) {
    is GroupAddress -> value
    Unassigned -> Unassigned.value
    is UnicastAddress -> value
}

internal fun PublishableAddress.toAddressArray(): AddressArray {
    val intAddress = value()
    val b1 = intAddress.shr(8).toByte()
    val b2 = intAddress.toByte()
    return AddressArray(b1, b2)
}
