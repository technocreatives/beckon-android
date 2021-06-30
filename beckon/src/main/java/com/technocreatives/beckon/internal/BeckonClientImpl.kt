package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import android.content.Context
import arrow.core.Either
import arrow.core.None
import arrow.core.Some
import arrow.core.computations.either
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import arrow.fx.coroutines.parTraverseEither
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonDeviceError
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.ConnectionError
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.DeviceDetail
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.SavedMetadata
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.checkRequirements
import com.technocreatives.beckon.data.DeviceRepository
import com.technocreatives.beckon.redux.BeckonAction
import com.technocreatives.beckon.redux.BeckonStore
import com.technocreatives.beckon.util.bluetoothManager
import com.technocreatives.beckon.util.connectedDevices
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.findDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber

internal class BeckonClientImpl(
    private val context: Context,
    private val beckonStore: BeckonStore,
    private val deviceRepository: DeviceRepository,
    private val bluetoothReceiver: Receiver,
    private val scanner: Scanner
) : BeckonClient {

    // TODO remove
//    private val bag = CompositeDisposable()

    override suspend fun startScan(setting: ScannerSetting): Flow<Either<ScanError, ScanResult>> {
        val originalScanStream = scanner.startScan(setting)
        return if (setting.useFilter) {
            val connected =
                beckonStore.currentState().connectedDevices.map { it.metadata().macAddress }
            val saved = deviceRepository.currentDevices().map { it.macAddress }
            originalScanStream
                .filterZ { it.device.address !in connected }
                .filterZ { it.device.address !in saved }
        } else {
            originalScanStream
        }
    }

    override suspend fun stopScan() {
        if (beckonStore.currentState().bluetoothState == BluetoothState.ON) {
            scanner.stopScan()
        } else {
            // TODO Callback to application? Notify failure
            Timber.e("Stopped scan but adapter is not turned on!")
        }
    }

    override suspend fun disconnectAllConnectedButNotSavedDevices(): Either<Throwable, Unit> {
        Timber.d("disconnectAllConnectedButNotSavedDevices is called")

        val currentSavedDevices = deviceRepository.currentDevices().map { it.macAddress }

        Timber.d("current saved devices: $currentSavedDevices")
        Timber.d("current connected devices: ${beckonStore.currentState().connectedDevices}")

        return beckonStore.currentState().connectedDevices
            .filter { it.metadata().macAddress !in currentSavedDevices }
            .map { disconnect(it) }
            .parTraverseEither(::identity)
            .map { }
    }

    override suspend fun search(
        setting: ScannerSetting,
        descriptor: Descriptor
    ): Flow<Either<ConnectionError, BeckonDevice>> {
        Timber.d("Search: $setting")
        val connectedDevicesInSystem = context.bluetoothManager()
            .connectedDevices()
            .filter { device -> setting.filters.any { it.filter(device) } }

        val connectedDevices = if (setting.useFilter) {
            val connected =
                beckonStore.currentState().connectedDevices.map { it.metadata().macAddress }
            val saved = deviceRepository.currentDevices().map { it.macAddress }
            Timber.d("Search connected: $connected saved: $saved")
            connectedDevicesInSystem
                .filter { it.address !in connected }
                .filter { it.address !in saved }
        } else {
            connectedDevicesInSystem
        }
        return flow {
            connectedDevices.forEach {
                emit(connectAndValidateRequirements(it, descriptor))
            }
        }

        // return Observable.fromArray(*connectedDevices.toTypedArray())
        //     .flatMapSingle { connect(it, descriptor) }
        //     .asFlow()
    }

    override suspend fun connect(
        result: ScanResult,
        descriptor: Descriptor
    ): Either<ConnectionError, BeckonDevice> {
        Timber.d("Connecting to: $result")
        return connectAndValidateRequirements(result.device, descriptor)
    }

    override suspend fun connect(metadata: SavedMetadata): Either<BeckonError, BeckonDevice> {
        Timber.d("connect SavedMetadata: $metadata")
        return when (
            val device =
                context.bluetoothManager().findDevice(metadata.macAddress)
        ) {
            is Some -> connectAndValidateRequirements(device.value, metadata.descriptor)
            is None -> BeckonDeviceError.BondedDeviceNotFound(metadata).left()
        }
    }

    override suspend fun disconnect(macAddress: MacAddress): Either<Throwable, MacAddress> {
        return when (val device = beckonStore.currentState().findConnectedDevice(macAddress)) {
            is None -> ConnectionError.ConnectedDeviceNotFound(macAddress).toException().left()
            is Some -> disconnect(device.value)
        }
    }

    override suspend fun save(macAddress: MacAddress): Either<Throwable, MacAddress> {
        return when (val device = beckonStore.currentState().findConnectedDevice(macAddress)) {
            is None -> ConnectionError.ConnectedDeviceNotFound(macAddress).toException().left()
            is Some -> either {
                device.value.createBond()
                deviceRepository.addDevice(device.value.metadata().savedMetadata())
                device.value.metadata().macAddress
            }
        }
    }

    override suspend fun remove(macAddress: MacAddress): Either<Throwable, MacAddress> {
        return beckonStore.currentState().findConnectedDevice(macAddress).fold(
            {
                deviceRepository.removeDevice(macAddress)
                macAddress.right()
            },
            { device ->
                deviceRepository.removeDevice(macAddress)
                disconnect(device)
            }
        )
    }

    override suspend fun findConnectedDevice(macAddress: MacAddress): Either<ConnectionError, BeckonDevice> {
        Timber.d("findConnectedDevice $macAddress in ${beckonStore.currentState()}")
        return when (val device = beckonStore.currentState().findConnectedDevice(macAddress)) {
            is None -> ConnectionError.ConnectedDeviceNotFound(macAddress).left()
            is Some -> device.value.right()
        }
    }

    override fun findConnectedDevice(metadata: SavedMetadata): Flow<Either<BeckonDeviceError, BeckonDevice>> {
        Timber.d("findConnectedDeviceO ${metadata.macAddress} in ${beckonStore.currentState()}")

        return beckonStore.states()
            .map { state ->
                Timber.d("State changed $state")
                when (val device = state.findConnectedDevice(metadata.macAddress)) {
                    is None -> {
                        when (state.findConnectingDevice(metadata.macAddress)) {
                            is None ->
                                if (context.bluetoothManager().adapter.isEnabled &&
                                    context.bluetoothManager()
                                        .findDevice(metadata.macAddress) is None
                                ) {
                                    BeckonDeviceError.BondedDeviceNotFound(metadata).left()
                                } else {
                                    BeckonDeviceError.ConnectedDeviceNotFound(metadata).left()
                                }
                            is Some -> BeckonDeviceError.Connecting(metadata).left()
                        }
                    }
                    is Some -> device.value.right()
                }
            }
    }

    override fun connectedDevices(): Flow<List<Metadata>> {
        return beckonStore.states().map { it.connectedDevices.map { it.metadata() } }
    }

    override suspend fun findSavedDevice(macAddress: MacAddress): Either<BeckonDeviceError.SavedDeviceNotFound, SavedMetadata> {
        return deviceRepository.findDevice(macAddress)
            .rightIfNotNull { BeckonDeviceError.SavedDeviceNotFound(macAddress) }
    }

    override fun savedDevices(): Flow<List<SavedMetadata>> {
        return deviceRepository.devices()
    }

    override suspend fun register(context: Context) {
        bluetoothReceiver.register(context)

        // do scan to check if bluetooth turn on from off state
        beckonStore.states()
//            .subscribeOn(Schedulers.io())
//            .observeOn(Schedulers.io())
            .map { it.bluetoothState }
            .distinctUntilChanged()
            .filter { it == BluetoothState.ON }
            .map { deviceRepository.currentDevices() }
            .collect { reconnectSavedDevices(it) }

        beckonStore.states()
            .map { it.bluetoothState }
            .distinctUntilChanged()
            .filter { it == BluetoothState.OFF }
            .collect {
                beckonStore.currentState().connectedDevices.onEach {
                    runBlocking(Dispatchers.IO) {
                        it.disconnect().fold(
                            {
                                Timber.d("Disconnect after BT_OFF success")
                            },
                            {
                                Timber.w("Disconnect after BT_OFF failed $it")
                            }
                        )
                    }
                }
                beckonStore.dispatch(BeckonAction.RemoveAllConnectedDevices)
            }
    }

    private suspend fun reconnectSavedDevices(devices: List<SavedMetadata>): Either<BeckonError, List<BeckonDevice>> {
        Timber.d("reconnectSavedDevices $devices")
        return devices.parTraverseEither { connect(it) }
    }

    override suspend fun unregister(context: Context) {
        bluetoothReceiver.unregister(context)
//        bag.clear()
    }

    override fun bluetoothState(): Flow<BluetoothState> {
        return beckonStore.states().map { it.bluetoothState }
    }

    override suspend fun write(
        macAddress: MacAddress,
        characteristic: CharacteristicSuccess.Write,
        data: Data
    ): Either<Throwable, Change> = either {
        val device = findConnectedDevice(macAddress).mapLeft { it.toException() }.bind()
        device.write(data, characteristic).bind()
    }

    override suspend fun read(
        macAddress: MacAddress,
        characteristic: CharacteristicSuccess.Read
    ): Either<Throwable, Change> = either {
        val device = findConnectedDevice(macAddress).mapLeft { it.toException() }.bind()
        device.read(characteristic).bind()
    }

    /**
     * Disconnect bluetooth and then remove that device from BeckonStore
     * in case of error remove it any way
     */
    private suspend fun disconnect(device: BeckonDevice): Either<Throwable, MacAddress> {
        return device.disconnect()
            .map { beckonStore.dispatch(BeckonAction.RemoveConnectedDevice(device)) }
            .map { device.metadata().macAddress }
    }

    /***
     * Connect to the bluetooth device
     * Verify if it satisfies requirement
     * if not disconnect
     * otherwise return BeckonDevice
     * TODO handle requirement better
     */
    private suspend fun connectAndValidateRequirements(
        device: BluetoothDevice,
        descriptor: Descriptor
    ): Either<ConnectionError, BeckonDevice> {
        Timber.d("Connect BluetoothDevice: $device")
        val manager = BeckonBleManager(context, device, descriptor)
        val savedMetadata = SavedMetadata(device.address, device.name, descriptor)
        beckonStore.dispatch(BeckonAction.AddConnectingDevice(savedMetadata))
        val result = either<ConnectionError, BeckonDevice> {
            val deviceDetail = manager.connect().bind()
            val metadata = checkRequirements(deviceDetail, descriptor, device).bind()
            val beckonDevice = BeckonDeviceImpl(device, manager, metadata)
            beckonStore.dispatch(BeckonAction.AddConnectedDevice(beckonDevice))
            beckonDevice
        }
        return result.mapLeft {
            if (it is ConnectionError.RequirementFailed) {
                manager.disconnect().enqueue()
            }
            beckonStore.dispatch(BeckonAction.RemoveConnectingDevice(savedMetadata))
            it
        }
    }

    private fun checkRequirements(
        detail: DeviceDetail,
        descriptor: Descriptor,
        device: BluetoothDevice
    ): Either<ConnectionError.RequirementFailed, Metadata> {
        Timber.d("Check Requirements $detail, $descriptor, $device")
        return checkRequirements(descriptor.requirements, detail.services, detail.characteristics)
            .map {
                Metadata(
                    device.address ?: "No Address",
                    device.name
                        ?: "No name",
                    detail.services, detail.characteristics, descriptor
                )
            }
    }
}
