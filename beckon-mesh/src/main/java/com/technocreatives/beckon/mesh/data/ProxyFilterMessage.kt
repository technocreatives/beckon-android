package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.AccessPayload

data class ProxyFilterMessage(val src: UnicastAddress, val payload: AccessPayload)