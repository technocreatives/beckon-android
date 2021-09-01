package com.technocreatives.beckon.mesh.extensions

fun ByteArray.toHex(): String =
    joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

