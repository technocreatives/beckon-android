package com.technocreatives.beckon

import android.content.Context
import androidx.annotation.MainThread
import io.reactivex.Observable
import io.reactivex.Single

interface BeckonClient {

    companion object {

        // return singleton instance of client
        fun create(context: Context): BeckonClient = BeckonClientImpl(context)
    }

    // scan available devices
    @MainThread
    fun scan(setting: ScannerSetting): Observable<BeckonScanResult>

    @MainThread
    fun scanList(setting: ScannerSetting): Observable<List<BeckonScanResult>>

    fun getDevice(macAddress: String): BeckonDevice?

    fun devices(): Observable<List<BeckonDevice>>
    fun getDevices(): List<BeckonDevice>

    // ??? need a better interface
    fun states(): Observable<List<DeviceChange>>

    fun saveDevices(devices: List<BeckonDevice>): Single<Boolean>

    fun connect(
        result: BeckonScanResult,
        characteristics: List<Characteristic>,
        autoConnect: Boolean
    ): BeckonDevice

    fun disconnect(device: BeckonDevice): Observable<ConnectionState>

    // fun states(device: Device): Observable<State>
}