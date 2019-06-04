package com.technocreatives.beckon.mock

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DiscoveredDevice
import com.technocreatives.beckon.justever
import io.reactivex.Observable

class MockDevice(
    private val metadata: DeviceMetadata,
    private val discoveredDevice: DiscoveredDevice,
    private val changesStream: Observable<Change>
) : BeckonDevice {

    private var isBondCreated = false

    override fun connectionStates(): Observable<ConnectionState> {
        return ConnectionState.Connected.justever()
    }

    override fun bondStates(): Observable<BondState> {
        return BondState.Bonded.justever()
    }

    override fun changes(): Observable<Change> {
        return changesStream
    }

    override fun currentState(): ConnectionState {
        return ConnectionState.Connected
    }

    override fun connect(): Observable<DiscoveredDevice> {
        return Observable.just(discoveredDevice)
    }

    override fun disconnect() {
    }

    override fun metadata(): DeviceMetadata {
        return metadata
    }

    override fun createBond() {
        isBondCreated = true
    }

    override fun removeBond() {
        isBondCreated = false
    }
}
