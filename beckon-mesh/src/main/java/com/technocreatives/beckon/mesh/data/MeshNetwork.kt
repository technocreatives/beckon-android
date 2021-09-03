package com.technocreatives.beckon.mesh.data

import no.nordicsemi.android.mesh.MeshNetwork

fun MeshNetwork.findAppKey(index: AppKeyIndex) =
    appKeys.find { index.value == it.keyIndex }

fun MeshNetwork.findNetKey(index: NetKeyIndex) =
    netKeys.find { index.value == it.keyIndex }
