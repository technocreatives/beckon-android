package com.technocreatives.beckon

import android.content.Context
import com.technocreatives.beckon.internal.BeckonClientImpl
import com.technocreatives.beckon.internal.BluetoothState
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

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

    /*===========================Scanning and connecting==========================*/

    fun startScan(setting: ScannerSetting)

    fun stopScan()

    /**
     * return stream of BeckonScanResult
     * it should be a stream that never complete
     * but it only may emit item only after startScan is called and before stopScan is called
     */
    fun scan(): Observable<BeckonScanResult>

    fun scanAndConnect(characteristics: List<Characteristic>): Observable<DeviceMetadata>

    fun disconnectAllConnectedButNotSavedDevices()
    fun disconnectAllExcept(addresses: List<String>)

    fun connect(
        result: BeckonScanResult,
        characteristics: List<Characteristic>
    ): Single<DeviceMetadata>

    fun disconnect(macAddress: String): Boolean

    /*===========================Device management==========================*/

    /**
     * Save a connected device for longer use
     * - find the device in set of connected device (return a device or DeviceNotFoundException)
     * - create bond if necessary (Bonded success or BondFailureException)
     * - save to database ( Complete or return SaveDeviceException)
     */
    fun save(macAddress: String): Completable

    /**
     * Remove a saved device
     * - Remove Bond ???
     * - Remove from database
     * - Remove from store
     */
    fun remove(macAddress: String): Completable

    // find a connected device
    fun findDevice(macAddress: MacAddress): Single<BeckonDevice>

    fun devices(): Observable<List<DeviceMetadata>>
    fun savedDevices(): Observable<List<DeviceMetadata>>
    fun currentDevices(): List<DeviceMetadata>
    fun connectedDevices(): Observable<List<DeviceMetadata>>

    fun register(context: Context)
    fun unregister(context: Context)

    fun bluetoothState(): Observable<BluetoothState>

    /*===========================Work with devices==========================*/

    fun write(macAddress: MacAddress, characteristic: CharacteristicDetail.Write, data: Data): Single<Change>
    fun write(macAddress: MacAddress, characteristicUuid: UUID, data: Data): Single<Change>
    fun read(macAddress: MacAddress, characteristic: CharacteristicDetail.Read): Single<Change>
}
