package com.technocreatives.beckon.mesh.extensions

import no.nordicsemi.android.mesh.MeshBeacon
import no.nordicsemi.android.mesh.MeshManagerApi

fun MeshManagerApi.getMeshBeacon(bytes: ByteArray): MeshBeacon? =
    getMeshBeaconData(bytes)?.let {
        getMeshBeacon(it)
    }

