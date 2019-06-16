package com.technocreatives.beckon.noop

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DiscoveredDevice
import io.reactivex.Observable

class NoopBeckonDevice(val metadata: DeviceMetadata) : BeckonDevice {

    override fun connectionStates(): Observable<ConnectionState> {
        return Observable.never()
    }

    override fun bondStates(): Observable<BondState> {
        return Observable.never()
    }

    override fun changes(): Observable<Change> {
        return Observable.never()
    }

    override fun currentState(): ConnectionState {
        return ConnectionState.Connected
    }

    override fun connect(): Observable<DiscoveredDevice> {
        return Observable.never()
    }

    override fun disconnect() {
    }

    override fun metadata(): DeviceMetadata {
        return metadata
    }

    override fun createBond() {
    }

    override fun removeBond() {
    }
}
