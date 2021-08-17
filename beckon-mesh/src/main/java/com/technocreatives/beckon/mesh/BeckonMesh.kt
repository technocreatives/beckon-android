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
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.support.v18.scanner.ScanSettings

class BeckonMesh(
    context: Context,
    private val beckonClient: BeckonClient,
    private val meshApi: BeckonMeshManagerApi
) {

    val stateSubject = MutableSharedFlow<MeshState>()
    private lateinit var currentState: Atomic<MeshState>

    suspend fun updateState(state: MeshState) {
        currentState.update { state }
        stateSubject.emit(state)
    }

    fun states(): Flow<MeshState> = stateSubject.asSharedFlow()
    suspend fun currentState(): MeshState = currentState.get()

    suspend fun startProvisioning(): Either<IllegalMeshStateError, Provisioning> {
        val state = currentState.get()
        return if (state is Loaded) {
            state.startProvisioning().right()
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

    suspend fun getProvisioningState(): Option<Provisioning> = TODO()
    suspend fun connectedState(): Either<Throwable, Connected> = TODO()

    //    fun identify(provisioning: ProvisioningState, scanResult: ScanResult) = TODO()
    suspend fun stopScan() {
        beckonClient.stopScan()
    }

    suspend fun scan(scannerSetting: ScannerSetting): Flow<Either<ScanError, List<ScanResult>>> {
        return beckonClient.scan(scannerSetting)
            .mapZ { it.sortedBy { it.macAddress } }
    }

    suspend fun scanForProvisioning(): Flow<Either<ScanError, List<ScanResult>>> {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setUseHardwareFilteringIfSupported(false)
            .build()

        val scannerSettings = ScannerSetting(
            settings,
            filters = listOf(
                DeviceFilter(
                    serviceUuid = MeshConstants.MESH_PROVISIONING_SERVICE_UUID.toString()
                )
            ),
            useFilter = false
        )
        return scan(scannerSettings)
    }

    internal suspend fun scanForProxy(): Flow<Either<ScanError, List<ScanResult>>> {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setUseHardwareFilteringIfSupported(false)
            .build()

        val scannerSettings = ScannerSetting(
            settings,
            filters = listOf(
                DeviceFilter(
                    serviceUuid = MeshConstants.MESH_PROXY_SERVICE_UUID.toString()
                )
            ),
            useFilter = false
        )
        return scan(scannerSettings)
    }

    internal suspend fun connectForProvisioning(scanResult: ScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult, ConnectionPhase.Provisioning)

    suspend fun connectForProxy(scanResult: ScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult, ConnectionPhase.Proxy)

    private suspend fun meshConnect(
        scanResult: ScanResult,
        method: ConnectionPhase
    ): Either<BeckonError, BeckonDevice> =
        either {
            val descriptor = Descriptor()
            val beckonDevice = beckonClient.connect(scanResult, descriptor).bind()
            val mtu = beckonDevice.requestMtu(MeshConstants.maxMtu).bind()
            beckonDevice.subscribe(method.dataOutCharacteristic()).bind()
            coroutineScope {
                launch { // todo is this the right way?
                    with(meshApi) {
                        beckonDevice.handleNotifications(method.dataOutCharacteristic())
                    }
                }
            }
            beckonDevice
        }
}

class BeckonMeshClient(val context: Context, val beckonClient: BeckonClient) {
    private val meshApi = BeckonMeshManagerApi(context, beckonClient)
//    suspend fun register() {
//    }

    suspend fun load(meshUuid: String): Either<Any, BeckonMesh> = either {
        // todo check the mesh ID here
        val networkLoadingEmitter =
            CompletableDeferred<Either<MeshLoadFailedError, Unit>>()

        meshApi.setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
            override fun onNetworkLoadFailed(error: String?) {
                networkLoadingEmitter.complete(Unit.right())
            }

            override fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
                networkLoadingEmitter.complete(MeshLoadFailedError("MeshNetwork is empty").left())
            }
        })

        meshApi.loadMeshNetwork()
        networkLoadingEmitter.await().bind()
        BeckonMesh(context, beckonClient, meshApi)
    }
}

class ProvisioningViewModel(val client: BeckonMeshClient, val meshUuid: String) {

    suspend fun doProvisioning(scanResult: ScanResult): Either<Any, Any> = either {
        val beckonMesh = client.load(meshUuid).bind()
        val provisioning = beckonMesh.startProvisioning().bind()
        val beckonDevice = provisioning.connect(scanResult).bind()
        val unprovisionedMeshNode = provisioning.identify(scanResult).bind()
        val provisionedMeshNode = provisioning.startProvisioning(unprovisionedMeshNode).bind()
        val proxyDevice = provisioning.scanAndConnect(provisionedMeshNode).bind()

        // what if the device is disconnected here ???
        provisioning.exchangeKeys(proxyDevice, provisionedMeshNode).bind()

        proxyDevice
    }

    suspend fun identify(scanResult: ScanResult): Either<Any, UnprovisionedMeshNode> = either {
        val beckonMesh = client.load(meshUuid).bind()
        val provisioning = beckonMesh.provisioningState().bind()
        val beckonDevice = provisioning.connect(scanResult).bind()
        val unprovisionedMeshNode = provisioning.identify(scanResult).bind()
        unprovisionedMeshNode
    }

    suspend fun doTheRest(
        unprovisionedMeshNode: UnprovisionedMeshNode,
        beckonDevice: BeckonDevice
    ): Either<Any, BeckonDevice> = either {

        val beckonMesh = client.load(meshUuid).bind()
        val provisioning = beckonMesh.provisioningState().bind()
        val provisionedMeshNode = provisioning.startProvisioning(unprovisionedMeshNode).bind()
//       beckonDevice.disconnect().bind()
        val proxyDevice = provisioning.scanAndConnect(provisionedMeshNode).bind()

        // what if the device is disconnected here ???
        provisioning.exchangeKeys(beckonDevice, provisionedMeshNode).bind()

        proxyDevice
    }
}