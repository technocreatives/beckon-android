package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.NetworkIdSerializer
import com.technocreatives.beckon.mesh.data.util.toHex
import kotlinx.serialization.Serializable

@Serializable(with = NetworkIdSerializer::class)
data class NetworkId(val value: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NetworkId

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    fun toHex() =
        value.toHex()

    override fun toString(): String =
        "NetworkId(value=${toHex()})"
}
