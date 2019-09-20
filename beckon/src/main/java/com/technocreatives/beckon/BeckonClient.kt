package com.technocreatives.beckon

import android.content.Context
import arrow.core.Either
import com.technocreatives.beckon.data.DeviceRepository
import com.technocreatives.beckon.data.DeviceRepositoryImpl
import com.technocreatives.beckon.extension.removeBond
import com.technocreatives.beckon.internal.BeckonClientImpl
import com.technocreatives.beckon.internal.BluetoothAdapterReceiver
import com.technocreatives.beckon.internal.ScannerImpl
import com.technocreatives.beckon.redux.createBeckonStore
import com.technocreatives.beckon.util.bluetoothManager
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.util.*

interface BeckonClient {

    companion object {
        private val beckonStore by lazy { createBeckonStore() }
        private val receiver by lazy { BluetoothAdapterReceiver(beckonStore) }
        private val scanner by lazy { ScannerImpl() }

        private lateinit var deviceRepository: DeviceRepository
        private lateinit var beckonClient: BeckonClient

        // return a singleton instance of client
        fun create(context: Context): BeckonClient {
            if (!::beckonClient.isInitialized) {
                deviceRepository = DeviceRepositoryImpl(context)
                beckonClient = BeckonClientImpl(context, beckonStore, deviceRepository, receiver, scanner)
            }

            return beckonClient
        }

        fun removeAllBonded(context: Context, filter: List<UUID> = emptyList()): Completable {
            val bondedDevices = context.bluetoothManager().adapter.bondedDevices
                    .filter {
                        if (filter.isEmpty()) {
                            true
                        } else {
                            val uuids = it.uuids.map { it.uuid }

                            for (uuid in filter) {
                                if (uuids.contains(uuid)) {
                                    return@filter true
                                }
                            }

                            false
                        }
                    }

            return Observable.fromIterable(bondedDevices)
                    .map {
                        Timber.d("Removing bond for: ${it.name} [${it.address}]")
                        it.removeBond()
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .ignoreElements()
        }
    }

    /*===========================Scanning and connecting==========================*/

    /**
     * return stream of @ScanResult
     * this may emit exception is something go wrong
     */

    fun startScan(setting: ScannerSetting): Observable<ScanResult>

    fun stopScan()

    fun disconnectAllConnectedButNotSavedDevices(): Completable

    fun search(setting: ScannerSetting, descriptor: Descriptor): Observable<Either<ConnectionError, BeckonDevice>>

    /*
    * Connect to a scanned device and then verify if all characteristics work
    * Return @BeckonDevice or ConnectFailedException when it fails
    * */
    fun connect(
        result: ScanResult,
        descriptor: Descriptor
    ): Single<BeckonDevice>

    fun connect(
        metadata: SavedMetadata
    ): Single<BeckonDevice>
    fun disconnect(macAddress: MacAddress): Completable

    /*===========================Device management==========================*/

    /**
     * Save a connected device for longer use
     * - find the device in set of connected device (return a device or DeviceNotFoundException)
     * - create bond if necessary (Bonded success or BondFailureException)
     * - save to database ( Complete or return SaveDeviceException)
     */
    fun save(macAddress: MacAddress): Single<MacAddress>

    /**
     * Remove a saved device
     * - Remove Bond ???
     * - Remove from database
     * - Remove from store
     */
    fun remove(macAddress: MacAddress): Single<MacAddress>

    // find a connected device
    fun findConnectedDevice(macAddress: MacAddress): Single<BeckonDevice>
    fun findConnectedDeviceO(metadata: SavedMetadata): Observable<Either<BeckonDeviceError, BeckonDevice>>
    fun connectedDevices(): Observable<List<Metadata>>

    fun findSavedDevice(macAddress: MacAddress): Single<SavedMetadata>
    fun savedDevices(): Observable<List<SavedMetadata>>
    fun clearAllSavedDevices(): Completable

    // fun changes(macAddress: MacAddress): Observable<Change>

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
