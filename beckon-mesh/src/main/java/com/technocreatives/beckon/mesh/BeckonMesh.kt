package com.technocreatives.beckon.mesh

import android.content.Context
import arrow.core.Either
import arrow.core.Option
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import arrow.fx.coroutines.Atomic
import com.technocreatives.beckon.*
import com.technocreatives.beckon.extensions.scan
import com.technocreatives.beckon.extensions.subscribe
import com.technocreatives.beckon.mesh.extensions.isNodeInTheMesh
import com.technocreatives.beckon.mesh.extensions.isProxyDevice
import com.technocreatives.beckon.mesh.extensions.toUnprovisionedScanResult
import com.technocreatives.beckon.mesh.model.*
import com.technocreatives.beckon.mesh.state.Connected
import com.technocreatives.beckon.mesh.state.Loaded
import com.technocreatives.beckon.mesh.state.MeshState
import com.technocreatives.beckon.mesh.state.Provisioning
import com.technocreatives.beckon.mesh.utils.tap
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapEither
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class BeckonMesh(
    context: Context,
    private val beckonClient: BeckonClient,
    private val meshApi: BeckonMeshManagerApi
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job

    // TODO Do we really need currentState? We can use stateSubject.value? Do we want Atomic?
    private lateinit var stateSubject: MutableStateFlow<MeshState>
    private lateinit var currentState: Atomic<MeshState>

    private suspend fun initState() {
        val initialState = Loaded(this, meshApi)
        currentState = Atomic.invoke(initialState)
        stateSubject = MutableStateFlow(initialState)
    }

    init {
        runBlocking {
            initState()
        }
    }

    fun nodes(): Flow<List<Node>> = meshApi.nodes()

    fun appKeys(): List<AppKey> =
        meshApi.appKeys()

    fun networkKeys(): List<NetworkKey> =
        meshApi.networkKeys()

    fun groups(): List<Group> =
        meshApi.groups()

    fun createGroup(name: String, address: Int): Group? {
        val network = meshApi.meshNetwork()
        return network.createGroup(network.selectedProvisioner, address, name)?.let { Group(it) }
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


    suspend fun getConnectedState(beckonDevice: BeckonDevice): Either<IllegalMeshStateError, Connected> {
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

    suspend fun stopScan() {
        beckonClient.stopScan()
    }

    private suspend fun scan(scannerSetting: ScannerSetting): Flow<Either<ScanError, List<ScanResult>>> {
        return beckonClient.scan(scannerSetting)
            .mapZ { it.sortedBy { it.macAddress } }
    }

    fun close() {
        job.cancel()
        meshApi.close()
    }

    fun <T> execute(f: suspend () -> T) =
        launch { f() }

    suspend fun scanForProvisioning(): Flow<Either<ScanError, List<UnprovisionedScanResult>>> {
        return scan(scanSetting(MeshConstants.MESH_PROVISIONING_SERVICE_UUID))
            .mapZ { it.mapNotNull { it.toUnprovisionedScanResult(meshApi) } }
    }

    suspend fun scanForProvisioning(node: Node): Either<BeckonError, BeckonDevice> =
        scanForProxyP()
            .mapZ {
                it.firstOrNull {
                    // TODO what if device is not proxy device? We do not need to connect to the current device.
                    meshApi.isProxyDevice(it.scanRecord!!, node.node) { stopScan() }
                }
            }.filterZ { it != null }
            .mapEither { connectForProxy(it!!.macAddress) }
            .first()

    private suspend fun scanForProxyP(): Flow<Either<ScanError, List<ScanResult>>> =
        scan(scanSetting(MeshConstants.MESH_PROXY_SERVICE_UUID))

    suspend fun scanForProxy(): Flow<Either<ScanError, List<ScanResult>>> =
        scan(scanSetting(MeshConstants.MESH_PROXY_SERVICE_UUID))
            .mapZ { it.filter { it.scanRecord != null && meshApi.isNodeInTheMesh(it.scanRecord!!) } }

    suspend fun connectForProvisioning(scanResult: UnprovisionedScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult.macAddress, MeshConstants.provisioningDataOutCharacteristic)

    suspend fun connectForProxy(macAddress: MacAddress): Either<BeckonError, BeckonDevice> =
        meshConnect(macAddress, MeshConstants.proxyDataOutCharacteristic)

    private suspend fun meshConnect(
        macAddress: MacAddress,
        characteristic: Characteristic
    ): Either<BeckonError, BeckonDevice> =
        either {
            val beckonDevice = beckonClient.connect(macAddress).bind()
            val mtu = beckonDevice.requestMtu(MeshConstants.maxMtu).bind()
            beckonDevice.subscribe(characteristic).bind()
            with(meshApi) {
                beckonDevice.handleNotifications(characteristic)
            }
            beckonDevice
        }

}