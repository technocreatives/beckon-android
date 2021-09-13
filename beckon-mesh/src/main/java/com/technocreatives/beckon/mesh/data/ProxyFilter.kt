package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.message.FilterType

data class ProxyFilter(val type: FilterType, val addresses: List<PublishableAddress>)