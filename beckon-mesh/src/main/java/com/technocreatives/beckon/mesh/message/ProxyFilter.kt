package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import arrow.core.continuations.either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.state.Connected
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.ProxyConfigAddAddressToFilter
import no.nordicsemi.android.mesh.transport.ProxyConfigFilterStatus
import no.nordicsemi.android.mesh.transport.ProxyConfigSetFilterType

// TODO define custom error here
suspend fun Connected.setAddressesToProxy(
    filterType: FilterType,
    addresses: List<PublishableAddress>
): Either<SendAckMessageError, Unit> = either {

    bearer.sendConfigMessage(SetProxyFilterType(filterType)).bind()

    if (addresses.isNotEmpty()) {
        bearer.sendConfigMessage(AddProxyConfigAddresses(addresses)).bind()
    }

}

@Serializable
@SerialName("SetProxyFilterType")
data class SetProxyFilterType(val filterType: FilterType) :
    ConfigMessage<ProxyConfigFilterResponse>() {
    override val responseOpCode = StatusOpCode.ProxyFilterType
    override val dst: Int = UnassignedAddress.value

    override fun toMeshMessage() = ProxyConfigSetFilterType(
        filterType.transform()
    )

    override fun fromResponse(message: MeshMessage): ProxyConfigFilterResponse =
        (message as ProxyConfigFilterStatus).transform()
}

@Serializable
data class ProxyConfigFilterResponse(
    override val dst: Int,
    override val src: Int,
    val filterType: FilterType,
    val listSize: Int,
) : ConfigStatusMessage()

fun ProxyConfigFilterStatus.transform() = ProxyConfigFilterResponse(
    dst,
    src,
    filterType.transform(),
    listSize
)

@Serializable
@SerialName("AddProxyConfigAddresses")
data class AddProxyConfigAddresses(val addresses: List<PublishableAddress>) :
    ConfigMessage<ProxyConfigFilterResponse>() {
    override val responseOpCode = StatusOpCode.ProxyFilterType
    override val dst: Int = UnassignedAddress.value
    override fun toMeshMessage() = ProxyConfigAddAddressToFilter(
        addresses.map { it.toAddressArray() }
    )

    override fun fromResponse(message: MeshMessage): ProxyConfigFilterResponse =
        (message as ProxyConfigFilterStatus).transform()
}