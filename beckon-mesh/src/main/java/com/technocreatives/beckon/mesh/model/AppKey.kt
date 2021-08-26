package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.ApplicationKey

class AppKey(private val actualKey: ApplicationKey) {
    val applicationKey get() = actualKey
    val keyIndex get() = actualKey.keyIndex

    val key = actualKey.key
    val name = actualKey.name
}