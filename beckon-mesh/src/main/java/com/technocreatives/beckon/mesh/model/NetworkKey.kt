package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.NetworkKey as NrfNetworkKey

class NetworkKey(internal val key: NrfNetworkKey) {
    val keyIndex get() = key.keyIndex
}