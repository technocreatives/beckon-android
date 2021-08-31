package com.technocreatives.beckon.mesh.extensions

import android.annotation.SuppressLint
import no.nordicsemi.android.mesh.NetworkKey
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode

@SuppressLint("RestrictedApi")
fun ProvisionedMeshNode.hasKey(networkKey: NetworkKey): Boolean =
    addedNetKeys.any { it.index == networkKey.keyIndex }