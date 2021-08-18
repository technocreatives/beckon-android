package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.ApplicationKey

class AppKey(private val appKey: ApplicationKey) {
    val applicationKey get() = appKey
}