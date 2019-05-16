package com.technocreatives.beckon

import io.reactivex.Observable

interface BeckonDevice {

    fun connectionStates(): Observable<ConnectionState>
    fun bondStates(): Observable<BondState>

    fun changes(): Observable<Change>

    fun currentState(): ConnectionState

    fun connect(): Observable<DiscoveredDevice>

    fun disconnect()

    fun metadata(): DeviceMetadata

    fun createBond()
    fun removeBond()

    // future supports operator
    // fun write(data): Observable<Change>
//    fun states(): Observable<DeviceState>
}