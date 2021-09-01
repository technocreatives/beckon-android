package com.technocreatives.beckon.mesh.extensions

import no.nordicsemi.android.mesh.ApplicationKey
import no.nordicsemi.android.mesh.NetworkKey

fun NetworkKey.info(): String =
    "Key $keyIndex: ${key.toHex()} - ${oldKey?.toHex()} - $phaseDescription"

fun ApplicationKey.info(): String =
    "Key $keyIndex: ${key.toHex()} - ${oldKey?.toHex()}"
