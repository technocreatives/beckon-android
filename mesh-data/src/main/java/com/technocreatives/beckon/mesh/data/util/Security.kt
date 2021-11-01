package com.technocreatives.beckon.mesh.data.util

import java.security.SecureRandom

fun generateRandomNumber(): ByteArray {
    val random = SecureRandom()
    val randomBytes = ByteArray(16)
    random.nextBytes(randomBytes)
    return randomBytes
}