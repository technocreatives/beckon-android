package com.technocreatives.beckon.mesh

import android.bluetooth.BluetoothDevice
import android.content.Context
import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.left
import arrow.core.right
import arrow.fx.coroutines.Atomic
import com.technocreatives.beckon.*
import com.technocreatives.beckon.extensions.scan
import com.technocreatives.beckon.extensions.subscribe
import com.technocreatives.beckon.internal.toUuid
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.extensions.isNodeInTheMesh
import com.technocreatives.beckon.mesh.extensions.toUnprovisionedScanResult
import com.technocreatives.beckon.mesh.model.ProxyScanResult
import com.technocreatives.beckon.mesh.model.UnprovisionedScanResult
import com.technocreatives.beckon.mesh.state.Connected
import com.technocreatives.beckon.mesh.state.Loaded
import com.technocreatives.beckon.mesh.state.MeshState
import com.technocreatives.beckon.mesh.state.Provisioning
import com.technocreatives.beckon.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import no.nordicsemi.android.mesh.ApplicationKey
import no.nordicsemi.android.mesh.NetworkKey
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import timber.log.Timber
import java.util.*
import kotlin.coroutines.CoroutineContext

class BeckonMesh(
    private val context: Context,
    private val beckonClient: BeckonClient,
    private val meshApi: BeckonMeshManagerApi,
    internal val config: BeckonMeshClientConfig,
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job

    // TODO Do we really need currentState? We can use stateSubject.value? Do we want Atomic?
    private lateinit var stateSubject: MutableStateFlow<MeshState>
    private lateinit var currentState: Atomic<MeshState>

    private val filteredMessages by lazy {
        MutableSharedFlow<ProxyFilterMessage>()
    }

    private suspend fun initState() {
        val initialState = Loaded(this, meshApi)
        currentState = Atomic.invoke(initialState)
        stateSubject = MutableStateFlow(initialState)
    }

    init {
        job.invokeOnCompletion { cause ->
            when (cause) {
                null -> {
                    Timber.w("BeckonMesh: $this is completed normally")
                }
                is CancellationException -> {
                    Timber.w(cause, "BeckonMesh: $this is cancelled normally")
                }
                else -> {
                    Timber.w(cause, "BeckonMesh: $this is failed")
                }
            }
        }
    }

    suspend fun register() {
        initState()
    }

    suspend fun unregister() {
        meshApi.close()
        job.cancel()
    }

    fun meshUuid(): UUID =
        meshApi.meshNetwork().meshUUID.toUuid()

    fun appKeys() =
        meshApi.meshNetwork().transform().appKeys

    fun networkKeys() =
        meshApi.meshNetwork().transform().netKeys

    fun netKey(index: NetKeyIndex): NetworkKey? =
        meshApi.meshNetwork().netKeys.find { it.keyIndex == index.value }

    fun appKey(index: AppKeyIndex): ApplicationKey? =
        meshApi.meshNetwork().appKeys.find { it.keyIndex == index.value }

    fun meshes(): StateFlow<MeshConfig> =
        meshApi.meshes()

    fun meshConfig(): MeshConfig =
        meshApi.meshNetwork().transform()

    fun proxyFilter(): ProxyFilter? =
        meshApi.proxyFilter()

    internal fun clearProxyFilter() {
        meshApi.meshNetwork().proxyFilter = null
    }

    fun proxyFilterMessages(): Flow<ProxyFilterMessage> =
        filteredMessages.asSharedFlow()

    // todo fix error
    fun createGroup(name: String, address: Int): Either<Throwable, Group> {
        val network = meshApi.meshNetwork()
        return Either.catch {
            val group = network.createGroup(network.selectedProvisioner, address, name)!!
            network.addGroup(group)
            group
        }.map {
            it.transform()
        }.mapLeft { IllegalArgumentException("${it.message} - $name, $address") }
    }

    // todo fix error
    fun deleteGroup(address: Int): Either<DeleteGroupError, Unit> {
        val network = meshApi.meshNetwork()
        val meshGroup = network.groups.firstOrNull { group -> group.address == address }
            ?: return DeleteGroupError.GroupDoesNotExist.left()

        if (network.getElements(meshGroup).isNotEmpty()) {
            return DeleteGroupError.GroupHasElements.left()
        }

        network.removeGroup(meshGroup)
        return Unit.right()
    }

    fun deleteNode(nodeId: NodeId): Boolean {
        val network = meshApi.meshNetwork()
        return network.findNode(nodeId)?.let {
            network.deleteNode(it)
        } ?: false
    }

    suspend fun updateState(state: MeshState) {
        Timber.d("updateState $state")
        currentState.update { state }
        stateSubject.emit(state)
    }

    fun states(): Flow<MeshState> = stateSubject.asStateFlow()
    suspend fun currentState(): MeshState = currentState.get()

    suspend fun startProvisioning(beckonDevice: BeckonDevice): Either<IllegalMeshStateError, Provisioning> {
        val state = currentState.get()
        return if (state is Loaded) {
            state.startProvisioning(beckonDevice).right()
        } else {
            IllegalMeshStateError(state).left()
        }
    }

    suspend fun startConnectedState(beckonDevice: BeckonDevice): Either<IllegalMeshStateError, Connected> {
        val state = currentState.get()
        return if (state is Loaded) {
            state.connect(beckonDevice).right()
        } else {
            IllegalMeshStateError(state).left()
        }
    }

    suspend fun provisioningState(): Either<IllegalMeshStateError, Provisioning> {
        val state = currentState.get()
        return if (state is Provisioning) {
            state.right()
        } else {
            IllegalMeshStateError(state).left()
        }
    }

    internal suspend inline fun <reified T : MeshState> isCurrentState(): Boolean {
        return currentState.get() is T
    }

    fun createConnectedState(beckonDevice: BeckonDevice): Connected =
        Connected(this, meshApi, filteredMessages, beckonDevice)

    suspend fun connectedState(): Either<IllegalMeshStateError, Connected> {
        val state = currentState.get()
        return if (state is Connected) {
            state.right()
        } else {
            IllegalMeshStateError(state).left()
        }
    }

    suspend fun stopScan() {
        beckonClient.stopScan()
    }

    private suspend fun scan(scannerSetting: ScannerSetting): Flow<Either<ScanError, List<ScanResult>>> {
        return beckonClient.scan(scannerSetting)
            .mapZ { it.sortedBy { it.macAddress } }
    }

    suspend fun disconnect(): Either<BleDisconnectError, Loaded> {
        Timber.d("BeckonMesh disconnect")
//        meshApi.close()
        return when (
            val state = currentState.get()) {
            is Loaded -> state.right()
            is Connected -> state.disconnect()
            is Provisioning -> state.cancel()
        }
    }

    internal fun <T> execute(f: suspend () -> T) =
        launch { f() }

    suspend fun scanForProvisioning(): Flow<Either<ScanError, List<UnprovisionedScanResult>>> {
        return scan(scanSetting(MeshConstants.MESH_PROVISIONING_SERVICE_UUID))
            .mapZ { it.mapNotNull { it.toUnprovisionedScanResult(meshApi) } }
    }

    suspend fun scanAfterProvisioning(
        node: ProvisionedMeshNode,
    ): Either<ScanError, ScanResult> =
        scanForNodeIdentity(node.unicastAddress)
            .mapZ { it.firstOrNull() }
            .filterZ { it != null }
            .mapZ { it!! }
            .first()

    suspend fun scanForProxy(): Flow<Either<ScanError, List<ScanResult>>> =
        scan(scanSetting(MeshConstants.MESH_PROXY_SERVICE_UUID))
            .mapZ { it.filter { it.scanRecord != null && meshApi.isNodeInTheMesh(it.scanRecord!!) } }

    suspend fun scanForWithData(): Flow<Either<ScanError, List<ProxyScanResult>>> = TODO()
//        scan(scanSetting(MeshConstants.MESH_PROXY_SERVICE_UUID))
//            .mapZ { it.map { it.transform() } }
//            .filterZ { it.filterNotNull() }

        suspend fun scanForProxy(filter: (BluetoothDevice) -> Boolean): Flow<Either<ScanError, List<ScanResult>>> {
        val cds = context.bluetoothManager().connectedDevices()
        Timber.d("All connected ble devices: $cds")

        val connectedDevices = context.bluetoothManager()
            .connectedDevices()
            .filter { filter(it) }
            .map { ScanResult(it, 1000, null) }

        return scanForProxy()
            .onStart { emitAll(flowOf(connectedDevices.right())) }

    }

    // scan for the device with a specific unicastAddress
    suspend fun scanForNodeIdentity(address: Int): Flow<Either<ScanError, List<ScanResult>>> {
        val scannerSetting = scanSetting(MeshConstants.MESH_PROXY_SERVICE_UUID)
        return scan(scannerSetting)
            .mapZ {
                it.filter {
                    it.scanRecord != null && meshApi.isNodeInTheMesh(
                        it.scanRecord!!,
                        address
                    )
                }
            }
    }

    suspend fun connectForProvisioning(
        scanResult: UnprovisionedScanResult,
        config: ConnectionConfig
    ): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult.macAddress, MeshConstants.provisioningDataOutCharacteristic, config)

    suspend fun connectForProxy(
        macAddress: MacAddress,
        config: ConnectionConfig
    ): Either<BeckonError, BeckonDevice> {
        Timber.d("execute Connect for proxy $macAddress")
        return meshConnect(macAddress, MeshConstants.proxyDataOutCharacteristic, config)
    }

    private suspend fun meshConnect(
        macAddress: MacAddress,
        characteristic: Characteristic,
        config: ConnectionConfig
    ): Either<BeckonError, BeckonDevice> =
        either {
            val beckonDevice = beckonClient.connect(macAddress).bind()
            if (config.expectedMtu != null) {
                beckonDevice.requestMtu(MeshConstants.maxMtu, config.expectedMtu).bind()
            } else {
                beckonDevice.requestMtu(MeshConstants.maxMtu)
            }
            beckonDevice.subscribe(characteristic).bind()
            with(meshApi) {
                handleNotifications(beckonDevice, characteristic)
            }
            beckonDevice
        }

}

sealed interface DeleteGroupError {
    object GroupDoesNotExist : DeleteGroupError
    object GroupHasElements : DeleteGroupError
}