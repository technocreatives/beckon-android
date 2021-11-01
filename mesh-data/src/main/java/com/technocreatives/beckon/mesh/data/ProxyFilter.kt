package com.technocreatives.beckon.mesh.data

data class ProxyFilter(val type: FilterType, val addresses: List<PublishableAddress>)

enum class FilterType {
    INCLUSION,
    EXCLUSION
}

