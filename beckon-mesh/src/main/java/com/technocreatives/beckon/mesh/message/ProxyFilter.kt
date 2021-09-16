package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.ProxyConfigAddAddressToFilter
import no.nordicsemi.android.mesh.transport.ProxyConfigSetFilterType

data class ProxyConfigStatus(
    val filterType: FilterType,
    val addresses: Int,
)

suspend fun Connected.setAddressesToProxy(
    filterType: FilterType,
    addresses: List<PublishableAddress>
): Either<SendAckMessageError, ProxyConfigStatus> = either {

    val filterTypeMessage = ProxyConfigSetFilterType(
        filterType.transform()
    )

    val filterTypeResult = bearer.setProxyConfigFilterType(filterTypeMessage).bind()

    val listSize = if (addresses.isNotEmpty()) {
        val addAddressesMessage = ProxyConfigAddAddressToFilter(
            addresses.map { it.toAddressArray() }
        )
        bearer.addProxyConfigAddressToFilter(addAddressesMessage).bind().listSize
    } else {
        0
    }

    ProxyConfigStatus(filterTypeResult.filterType.transform(), listSize)
}