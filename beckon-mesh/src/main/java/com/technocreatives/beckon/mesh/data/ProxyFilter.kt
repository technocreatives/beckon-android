package com.technocreatives.beckon.mesh.data

import no.nordicsemi.android.mesh.utils.AddressArray
import no.nordicsemi.android.mesh.utils.ProxyFilterType
import no.nordicsemi.android.mesh.utils.ProxyFilter as NrfProxyFilter

data class ProxyFilter(val type: FilterType, val addresses: List<PublishableAddress>)

enum class FilterType {
    INCLUSION,
    EXCLUSION
}

internal fun FilterType.transform() = when (this) {
    FilterType.INCLUSION -> ProxyFilterType(ProxyFilterType.INCLUSION_LIST_FILTER)
    FilterType.EXCLUSION -> ProxyFilterType(ProxyFilterType.EXCLUSION_LIST_FILTER)
}

internal fun ProxyFilterType.transform() = when (this.type) {
    ProxyFilterType.INCLUSION_LIST_FILTER -> FilterType.INCLUSION
    ProxyFilterType.EXCLUSION_LIST_FILTER -> FilterType.EXCLUSION
    else -> throw IllegalArgumentException("Invalid filter type value: $type")
}

internal fun AddressArray.transform(): PublishableAddress =
    PublishableAddress.from(address)


internal fun NrfProxyFilter.transform() =
    ProxyFilter(
        filterType.transform(),
        addresses.map { it.transform() }
    )
