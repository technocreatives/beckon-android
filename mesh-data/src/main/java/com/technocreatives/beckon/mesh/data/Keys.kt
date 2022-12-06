package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.KeySerializer
import com.technocreatives.beckon.mesh.data.serializer.NetKeySecuritySerializer
import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeToLongSerializer
import com.technocreatives.beckon.mesh.data.util.SecureUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

// This can only have a value of 0-4095
@Serializable
@JvmInline
value class NetKeyIndex(val value: Int)

// This can only have a value of 0-4095
@Serializable
@JvmInline
value class AppKeyIndex(val value: Int)

@Serializable
data class NetKey(
    val name: String,
    val index: NetKeyIndex,
    val phase: Int = NORMAL_OPERATION,
    val key: Key,
    @SerialName("minSecurity")
    @Serializable(with = NetKeySecuritySerializer::class)
    val isSecurity: Boolean = false,
    val oldKey: Key? = null,
    @Serializable(with = OffsetDateTimeToLongSerializer::class)
    val timestamp: Long = Instant.now().toEpochMilli(),
) {
    companion object {
        // Key refresh phases
        const val NORMAL_OPERATION = 0
        const val KEY_DISTRIBUTION = 1
        const val USING_NEW_KEYS = 2

        // Transitions
        const val USE_NEW_KEYS = 2 //Normal operation
        const val REVOKE_OLD_KEYS = 3 //Key Distribution
    }

    fun identityKey(): Key = SecureUtils.calculateIdentityKey(key)
    fun networkId() = toNetworkId(key)
    fun oldNetworkId() = oldKey?.let { toNetworkId(it) }

    fun isNetworkIdMatch(id: NetworkId): Boolean =
       networkId() == id || oldNetworkId() == id

    private fun toNetworkId(key: Key) = NetworkId(SecureUtils.calculateK3(key.value))
}

@Serializable
data class AppKey(
    val name: String,
    val index: AppKeyIndex,
    val boundNetKey: NetKeyIndex,

    val key: Key,
    val oldKey: Key? = null,
)


@Serializable(with = KeySerializer::class)
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