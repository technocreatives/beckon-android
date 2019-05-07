package com.technocreatives.beckon

import android.content.Context
import com.technocreatives.beckon.internal.BeckonClientImpl
import io.reactivex.Observable

interface BeckonClient {

    companion object {
        // todo using by lazy
        private var beckonClient: BeckonClient? = null

        // return a singleton instance of client
        fun create(context: Context): BeckonClient {
            if (beckonClient == null) {
                beckonClient = BeckonClientImpl(context)
            }
            return beckonClient!!
        }
    }

    fun scan(setting: ScannerSetting): Observable<BeckonScanResult>
    fun scanList(setting: ScannerSetting): Observable<List<BeckonScanResult>>

    fun scanAndConnect(descriptor: Descriptor): Observable<DiscoveredDevice>

    fun save(device: BeckonDevice): Observable<Boolean>
    fun remove(device: BeckonDevice): Observable<Boolean>

    fun findDevice(macAddress: String): Observable<BeckonDevice>

    fun devices(): Observable<List<DeviceInfo>>
    fun getDevices(): List<BeckonDevice>

    fun connect(
        result: BeckonScanResult,
        characteristics: List<Characteristic>
    ): Observable<DiscoveredDevice>

    fun disconnect(device: DeviceInfo): Boolean

    fun register(context: Context)
    fun unregister(context: Context)

    //    fun saveDevices(devices: List<BeckonDevice>): Single<Boolean>
    // ??? need a better interface
//    fun states(): Observable<List<DeviceChange>>
}