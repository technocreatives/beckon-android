package com.technocreatives.beckon.redux

internal data class BeckonState(val devices: List<Device>)

internal data class Device(val address: String)
