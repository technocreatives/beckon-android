package com.technocreatives.beckon.mock

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicResult
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.WritableDeviceMetadata
import com.technocreatives.beckon.justever
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data

class MockDevice(
    private val metadata: WritableDeviceMetadata, // todo remove this
    private val discoveredDevice: DeviceMetadata,
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

    override fun disconnect() {
    }

    override fun metadata(): WritableDeviceMetadata {
        return metadata
    }

    override fun createBond() {
        isBondCreated = true
    }

    override fun removeBond() {
        isBondCreated = false
    }

    override fun write(data: Data, characteristic: CharacteristicResult.Write): Single<Change> {
        return Single.just(Change(characteristic.characteristic, data))
    }
}
