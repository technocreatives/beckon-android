package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.NetworkIdSerializer
import kotlinx.serialization.Serializable

@JvmInline
@Serializable(with = NetworkIdSerializer::class)
value class NetworkId(val value: ByteArray) {
   fun equal(other: NetworkId) =
       value.contentEquals(other.value)
}