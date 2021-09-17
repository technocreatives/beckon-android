package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.ProxyConfigAddAddressToFilter
import no.nordicsemi.android.mesh.transport.ProxyConfigSetFilterType

// TODO define custom error here
suspend fun Connected.setAddressesToProxy(
    filterType: FilterType,
    addresses: List<PublishableAddress>
): Either<SendAckMessageError, Unit> = either {

    val filterTypeMessage = ProxyConfigSetFilterType(
        filterType.transform()
    )

    bearer.setProxyConfigFilterType(filterTypeMessage).bind()

    if (addresses.isNotEmpty()) {
        val addAddressesMessage = ProxyConfigAddAddressToFilter(
            addresses.map { it.toAddressArray() }
        )
        bearer.addProxyConfigAddressToFilter(addAddressesMessage).bind().listSize
    }

}