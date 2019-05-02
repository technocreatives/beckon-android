package com.technocreatives.beckon

import io.reactivex.Observable

interface BeckonDevice {

    fun connectionStates(): Observable<ConnectionState>

    fun changes(): Observable<Change>

    fun currentState(): ConnectionState

    fun doConnect(): Observable<ConnectionState>

    fun doDisconnect(): Observable<ConnectionState>

    fun deviceData(): DeviceInfo

    // future supports operator
    // fun write(data): Observable<Change>
//    fun states(): Observable<DeviceState>
}