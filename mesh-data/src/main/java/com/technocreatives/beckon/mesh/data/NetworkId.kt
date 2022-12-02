package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.NetworkIdSerializer
import com.technocreatives.beckon.mesh.data.util.toHex
import kotlinx.serialization.Serializable

@JvmInline
@Serializable(with = NetworkIdSerializer::class)
value class NetworkId(val value: ByteArray) {

    fun isEqual(other: NetworkId) =
        value.contentEquals(other.value)

    fun toHex() =
        value.toHex()

    override fun toString(): String =
        "NetworkId(value=${toHex()})"

}