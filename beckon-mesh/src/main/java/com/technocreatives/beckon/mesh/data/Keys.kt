package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.KeySerializer
import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import no.nordicsemi.android.mesh.ApplicationKey
import java.time.LocalDateTime
import java.time.OffsetDateTime

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
    val minSecurity: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val timestamp: OffsetDateTime,
)

@Serializable
data class AppKey(
    val name: String,
    val index: AppKeyIndex,
    val boundNetKey: NetKeyIndex,

    @Serializable(with = KeySerializer::class)
    val key: Key,
)

fun ApplicationKey.transform(): AppKey = AppKey(
    name, AppKeyIndex(keyIndex), NetKeyIndex(boundNetKeyIndex), Key(key)
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