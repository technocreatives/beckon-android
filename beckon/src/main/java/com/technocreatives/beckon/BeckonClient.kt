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

    fun startScan(setting: ScannerSetting)
    fun stopScan()

    fun scan(): Observable<BeckonScanResult>
    fun scanAndConnect(characteristics: List<Characteristic>): Observable<DiscoveredDevice>

    fun disconnectAllConnectedDevicesButNotSavedDevices()

    // single
    fun save(macAddress: String): Observable<Unit>
    // single
    fun remove(macAddress: String): Observable<Unit>

    // single
    fun findDevice(macAddress: String): Observable<BeckonDevice>
    fun devices(): Observable<List<DeviceMetadata>>
    fun currentDevices(): List<DeviceMetadata>

    // single
    fun connect(
        result: BeckonScanResult,
        characteristics: List<Characteristic>
    ): Observable<DiscoveredDevice>

    fun disconnect(macAddress: String): Boolean

    fun register(context: Context)
    fun unregister(context: Context)
}

// BeckonScanConnectHelper(client: BeckonClient) {
//     fun scanAndConnect(characteristics: List<Characteristic>): Observable<DiscoveredDevice>
//     fun connect(device: DiscoveredDevice, List<DiscoveredDevice>): Observable<Unit>
// }