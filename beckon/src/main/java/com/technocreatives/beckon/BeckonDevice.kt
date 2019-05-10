package com.technocreatives.beckon

import io.reactivex.Observable

interface BeckonDevice {

    fun connectionStates(): Observable<ConnectionState>

    fun changes(): Observable<Change>

    fun currentState(): ConnectionState

    fun connect(): Observable<DiscoveredDevice>

    fun disconnect()

    fun metadata(): DeviceMetadata

    // future supports operator
    // fun write(data): Observable<Change>
//    fun states(): Observable<DeviceState>
}