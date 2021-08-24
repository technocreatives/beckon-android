package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.ApplicationKey

class AppKey(internal val key: ApplicationKey) {
    internal val applicationKey get() = key
    val keyIndex get() = key.keyIndex
}