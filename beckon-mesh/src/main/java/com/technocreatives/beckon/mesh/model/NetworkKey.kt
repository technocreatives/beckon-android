package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.NetworkKey as NrfNetworkKey

class NetworkKey(internal val actualKey: NrfNetworkKey) {
    val keyIndex get() = actualKey.keyIndex

    val name = actualKey.name
    val key = actualKey.key
}
