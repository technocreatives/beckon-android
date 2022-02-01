package com.technocreatives.beckon.redux

import com.lenguyenthanh.redux.core.Log
import com.lenguyenthanh.redux.flow.FlowStore
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.SavedMetadata

fun beckonDeviceForRemoving(macAddress: MacAddress): MacAddress {
//    val device = mock<BeckonDevice>()
//    val metadata = mock<Metadata>()
//    whenever(metadata.macAddress).thenReturn(macAddress)
//    whenever(device.metadata()).thenReturn(metadata)
//    return device
    return macAddress
}

fun beckonDevice(macAddress: MacAddress): BeckonDevice {
    val device = mock<BeckonDevice>()
    val metadata = mock<Metadata>()
    whenever(metadata.macAddress).thenReturn(macAddress)
    whenever(device.metadata()).thenReturn(metadata)
    return device
}

fun savedMetadata(macAddress: MacAddress): SavedMetadata {
    val metadata = mock<SavedMetadata>()
    whenever(metadata.macAddress).thenReturn(macAddress)
    return metadata
}

internal fun testBeckonStore(state: BeckonState): BeckonStore {
    return FlowStore(
        reducer, { state },
        log = object : Log {
            override fun log(tag: String, message: String) {
                println("$tag: $message")
            }
        }
    )
}

internal val initialState = BeckonState(emptyList(), emptyList(), BluetoothState.UNKNOWN)
