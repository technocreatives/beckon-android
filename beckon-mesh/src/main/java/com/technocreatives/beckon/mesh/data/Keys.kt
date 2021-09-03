package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.KeySerializer
import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeSerializer
import com.technocreatives.beckon.mesh.data.serializer.NetKeySecuritySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class NetKeyIndex(val value: Int)

@Serializable
@JvmInline
value class AppKeyIndex(val value: Int)

@Serializable
data class NetKey(
    val name: String,
    val index: NetKeyIndex,
    @Serializable(with = KeySerializer::class)
    val key: Key,
    val phase: Int,
    @SerialName("minSecurity")
    @Serializable(with = NetKeySecuritySerializer::class)
    val isSecurity: Boolean,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val timestamp: Long,
)

@Serializable
data class AppKey(
    val name: String,
    val index: AppKeyIndex,
    val boundNetKey: NetKeyIndex,

    @Serializable(with = KeySerializer::class)
    val key: Key,
)

@Serializable
data class Key(val value: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Key

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}