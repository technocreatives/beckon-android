package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.ProxyConfigAddAddressToFilter
import no.nordicsemi.android.mesh.transport.ProxyConfigFilterStatus
import no.nordicsemi.android.mesh.transport.ProxyConfigSetFilterType
import no.nordicsemi.android.mesh.utils.MeshAddress

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

data class SetProxyFilterType(val filterType: FilterType) : ConfigMessage {
    override val responseOpCode = StatusOpCode.ProxyFilterType
    override val dst: Int = UnassignedAddress.value

    override fun toMeshMessage() = ProxyConfigSetFilterType(
        filterType.transform()
    )
}

data class ProxyConfigFilterResponse(
    override val dst: Int,
    override val src: Int,
    val filterType: FilterType,
    val listSize: Int,
) : ConfigStatusMessage

fun ProxyConfigFilterStatus.transform() = ProxyConfigFilterResponse(
    dst,
    src,
    filterType.transform(),
    listSize
)

data class AddProxyConfigAddresses(val addresses: List<PublishableAddress>) : ConfigMessage {
    override val responseOpCode = StatusOpCode.ProxyFilterType
    override val dst: Int = UnassignedAddress.value
    override fun toMeshMessage() = ProxyConfigAddAddressToFilter(
        addresses.map { it.toAddressArray() }
    )
}