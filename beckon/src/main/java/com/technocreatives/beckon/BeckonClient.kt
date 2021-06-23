package com.technocreatives.beckon

import android.content.Context
import arrow.core.Either
import com.technocreatives.beckon.data.DeviceRepositoryImpl
import com.technocreatives.beckon.internal.BeckonClientImpl
import com.technocreatives.beckon.internal.BluetoothAdapterReceiver
import com.technocreatives.beckon.internal.ScannerImpl
import com.technocreatives.beckon.redux.createBeckonStore
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data

interface BeckonClient {

    companion object {
        // todo using by lazy

        private var beckonClient: BeckonClient? = null

        // return a singleton instance of client
        fun create(context: Context): BeckonClient {
            val beckonStore = createBeckonStore()
            val deviceRepository = DeviceRepositoryImpl(context)
            val receiver = BluetoothAdapterReceiver(beckonStore)
            val scanner = ScannerImpl()

            if (beckonClient == null) {
                beckonClient = BeckonClientImpl(context, beckonStore, deviceRepository, receiver, scanner)
            }
            return beckonClient!!
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
    fun search(setting: ScannerSetting, descriptor: Descriptor): Observable<Either<ConnectionError, BeckonDevice>>

    /*
    * Connect to a scanned device and then verify if all characteristics work
    * Return @BeckonDevice or ConnectFailedException when it fails
    * */
    fun connect(
        result: ScanResult,
        descriptor: Descriptor
    ): Single<BeckonDevice>

    /*
    * Connect to a saved device and then verify if all characteristics work
    * This function only works with bonded device
    * Return @BeckonDevice or ConnectFailedException when it fails
    * */
    fun connect(
        metadata: SavedMetadata
    ): Single<BeckonDevice>
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

    fun findConnectedDevice(macAddress: MacAddress): Single<BeckonDevice>
    /**
     * Return a stream of state
     */
    fun findConnectedDeviceO(metadata: SavedMetadata): Observable<Either<BeckonDeviceError, BeckonDevice>>
    fun connectedDevices(): Observable<List<Metadata>>

    fun findSavedDevice(macAddress: MacAddress): Single<SavedMetadata>
    fun savedDevices(): Observable<List<SavedMetadata>>

    // hook up functions
    fun register(context: Context)

    fun unregister(context: Context)

    // utilities
    fun bluetoothState(): Observable<BluetoothState>

    /*===========================Work with devices==========================*/
    fun write(macAddress: MacAddress, characteristic: CharacteristicSuccess.Write, data: Data): Single<Change>
    fun read(macAddress: MacAddress, characteristic: CharacteristicSuccess.Read): Single<Change>
    // todo add subscribe function
}
