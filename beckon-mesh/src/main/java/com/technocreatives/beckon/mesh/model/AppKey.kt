package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.ApplicationKey

class AppKey(private val actualKey: ApplicationKey) {
    internal val applicationKey get() = actualKey
    val keyIndex get() = actualKey.keyIndex

    val key = actualKey.key
    val name = actualKey.name
}