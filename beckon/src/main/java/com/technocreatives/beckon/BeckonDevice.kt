package com.technocreatives.beckon

import io.reactivex.Observable

interface BeckonDevice {

    // Device info
    fun macAddress(): String

    fun name(): String

    fun connectionState(): Observable<ConnectionState>

    fun changes(): Observable<Change>

    fun states(): Observable<DeviceState>

    fun currentStates(): List<Change>

    fun doConnect(autoConnect: Boolean): Observable<ConnectionState> // [Connecting, connected, Failed, complete]

    fun doDisconnect(): Observable<ConnectionState>
    // future supports operator
    // fun write(data): Observable<Change>
}