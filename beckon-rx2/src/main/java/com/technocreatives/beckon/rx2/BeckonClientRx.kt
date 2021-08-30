package com.technocreatives.beckon.rx2

import android.content.Context
import arrow.core.Either
import com.technocreatives.beckon.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data

interface BeckonClientRx {

    companion object {
        // return a singleton instance of client
        fun create(context: Context): BeckonClientRx {
            return BeckonClient.create(context).rx()
        }
    }

    /*===========================Scanning and connecting==========================*/

    /**
     * return stream of @ScanResult
     * this may throw exception if something goes wrong
     */
    fun startScan(setting: ScannerSetting): Observable<ScanResult>

    fun stopScan()

    fun disconnectAllConnectedButNotSavedDevices(): Completable

    /**
     * Search for all currently connected devices in the systems which satisfies ScannerSetting
     * If setting.useFilter == True, this function will ignore all connected and saved devices in Beckon
     */
    fun search(
        setting: ScannerSetting,
        descriptor: Descriptor
    ): Observable<Either<ConnectionError, BeckonDeviceRx>>

    /*
    * Connect to a scanned device and then verify if all characteristics work
    * Return @BeckonDevice or ConnectFailedException when it fails
    * */
    fun connect(
        result: ScanResult,
        descriptor: Descriptor
    ): Single<BeckonDeviceRx>

    /*
    * Connect to a saved device and then verify if all characteristics work
    * This function only works with bonded device
    * Return @BeckonDevice or ConnectFailedException when it fails
    * */
    fun connect(
        metadata: SavedMetadata
    ): Single<BeckonDeviceRx>

    fun disconnect(macAddress: MacAddress): Completable

    /*===========================Device management==========================*/

    /**
     * Save a connected device for longer use
     * - find the device in set of connected device (return a device or @ConnectedDeviceNotFound)
     * - create bond if necessary (Bonded success or @CreateBondFailed)
     * - save to database
     */
    fun save(macAddress: MacAddress): Single<MacAddress>

    /**
     * Remove a saved device
     * - Remove from database
     * - Remove from store
     * - This function does not remove Bond so you have to removeBond before use this function if you want.
     */
    fun remove(macAddress: MacAddress): Single<MacAddress>

    fun findConnectedDevice(macAddress: MacAddress): Single<BeckonDeviceRx>

    /**
     * Return a stream of state
     */
    fun findConnectedDeviceO(metadata: SavedMetadata): Observable<Either<BeckonDeviceError, com.technocreatives.beckon.rx2.BeckonDeviceRx>>
    fun connectedDevices(): Observable<List<Metadata>>

    fun findSavedDevice(macAddress: MacAddress): Single<SavedMetadata>
    fun savedDevices(): Observable<List<SavedMetadata>>

    // hook up functions
    fun register(context: Context)

    fun unregister(context: Context)

    // utilities
    fun bluetoothState(): Observable<BluetoothState>

    /*===========================Work with devices==========================*/
    fun write(
        macAddress: MacAddress,
        characteristic: FoundCharacteristic.Write,
        data: Data
    ): Single<Change>

    fun read(macAddress: MacAddress, characteristic: FoundCharacteristic.Read): Single<Change>
    // todo add subscribe function
}
