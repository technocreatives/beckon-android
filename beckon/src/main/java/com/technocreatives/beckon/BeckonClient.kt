package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import android.content.Context
import arrow.core.Either
import com.technocreatives.beckon.data.DeviceRepositoryImpl
import com.technocreatives.beckon.internal.BeckonClientImpl
import com.technocreatives.beckon.internal.BluetoothAdapterReceiver
import com.technocreatives.beckon.internal.ScannerImpl
import com.technocreatives.beckon.redux.createBeckonStore
import kotlinx.coroutines.flow.Flow
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
                beckonClient =
                    BeckonClientImpl(context, beckonStore, deviceRepository, receiver, scanner)
            }
            return beckonClient!!
        }
    }

    /*===========================Scanning and connecting==========================*/
    suspend fun startScan(setting: ScannerSetting): Flow<Either<ScanError, ScanResult>>

    suspend fun stopScan()

    suspend fun disconnectAllConnectedButNotSavedDevices(): Either<Throwable, Unit>

    /**
     * Search for all currently connected devices in the systems which satisfies ScannerSetting
     * If setting.useFilter == True, this function will ignore all connected and saved devices in Beckon
     */
    suspend fun searchAndConnect(
        setting: ScannerSetting,
        descriptor: Descriptor
    ): Flow<Either<ConnectionError, BeckonDevice>>

    suspend fun search(setting: ScannerSetting): List<BluetoothDevice>
    /*
    * Connect to a scanned device and then verify if all characteristics work
    * Return @BeckonDevice or ConnectFailedException when it fails
    * */
    suspend fun connect(
        result: ScanResult,
        descriptor: Descriptor
    ): Either<ConnectionError, BeckonDevice>

    /*
   * Connect to a scanned device and then verify if all characteristics work
   * Return @BeckonDevice or ConnectFailedException when it fails
   * */
    suspend fun connect(
        macAddress: MacAddress,
        descriptor: Descriptor = Descriptor()
    ): Either<ConnectionError, BeckonDevice>

    /*
    * Connect to a saved device and then verify if all characteristics work
    * This function only works with bonded device
    * Return @BeckonDevice or ConnectFailedException when it fails
    * */
    suspend fun connect(
        metadata: SavedMetadata
    ): Either<BeckonError, BeckonDevice>

    suspend fun disconnect(macAddress: MacAddress): Either<Throwable, MacAddress>

    /*===========================Device management==========================*/

    /**
     * Save a connected device for longer use
     * - find the device in set of connected device (return a device or @ConnectedDeviceNotFound)
     * - create bond if necessary (Bonded success or @CreateBondFailed)
     * - save to database
     */
    suspend fun save(macAddress: MacAddress): Either<Throwable, MacAddress>

    /**
     * Remove a saved device
     * - Remove from database
     * - Remove from store
     * - This function does not remove Bond so you have to removeBond before use this function if you want.
     */
    suspend fun remove(macAddress: MacAddress): Either<Throwable, MacAddress>

    suspend fun findConnectedDevice(macAddress: MacAddress): Either<ConnectionError, BeckonDevice>

    /**
     * Return a stream of state
     */
    fun findConnectedDevice(metadata: SavedMetadata): Flow<Either<BeckonDeviceError, BeckonDevice>>
    fun connectedDevices(): Flow<List<Metadata>>

    suspend fun findSavedDevice(macAddress: MacAddress): Either<BeckonDeviceError.SavedDeviceNotFound, SavedMetadata>
    fun savedDevices(): Flow<List<SavedMetadata>>

    // hook up functions
    fun register(context: Context)

    fun unregister(context: Context)

    // utilities
    // todo remove
    fun bluetoothState(): Flow<BluetoothState>

    /*===========================Work with devices==========================*/
    suspend fun write(
        macAddress: MacAddress,
        characteristic: FoundCharacteristic.Write,
        data: Data
    ): Either<Throwable, Change>

    suspend fun read(
        macAddress: MacAddress,
        characteristic: FoundCharacteristic.Read
    ): Either<Throwable, Change>
    // todo add subscribe function
}
