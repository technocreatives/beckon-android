package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.ProxyConfigAddAddressToFilter
import no.nordicsemi.android.mesh.transport.ProxyConfigSetFilterType
import no.nordicsemi.android.mesh.utils.AddressArray
import no.nordicsemi.android.mesh.utils.ProxyFilterType

enum class FilterType {
    INCLUSION,
    EXCLUSION
}

data class ProxyConfigStatus(
    val filterType: FilterType,
    val size: Int
)

suspend fun Connected.setAddressesToProxy(
    filterType: FilterType,
    addresses: List<GroupAddress>
): Either<SendAckMessageError, ProxyConfigStatus> = either {

    val filterTypeMessage = ProxyConfigSetFilterType(
        filterType.transform()
    )

    val addAddressesMessage = ProxyConfigAddAddressToFilter(
        addresses.map { it.toAddressArray() }
    )

    bearer.setProxyConfigFilterType(filterTypeMessage).bind()
    val result = bearer.addProxyConfigAddressToFilter(addAddressesMessage).bind()

    ProxyConfigStatus(result.filterType.transform(), result.listSize)
}

private fun FilterType.transform() = when (this) {
    FilterType.INCLUSION -> ProxyFilterType(ProxyFilterType.INCLUSION_LIST_FILTER)
    FilterType.EXCLUSION -> ProxyFilterType(ProxyFilterType.EXCLUSION_LIST_FILTER)
}

private fun ProxyFilterType.transform() = when (this.type) {
    ProxyFilterType.INCLUSION_LIST_FILTER -> FilterType.INCLUSION
    ProxyFilterType.EXCLUSION_LIST_FILTER -> FilterType.EXCLUSION
    else -> throw IllegalArgumentException("Invalid filter type value: $type")
}

private fun GroupAddress.toAddressArray(): AddressArray {
    val intAddress = value
    val b1 = intAddress.shr(8).toByte()
    val b2 = intAddress.toByte()
    return AddressArray(b1, b2)
}
