package com.technocreatives.beckon.mesh

import android.content.Context
import android.os.ParcelUuid
import android.os.Parcelable
import arrow.core.Either
import arrow.core.Option
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import arrow.fx.coroutines.Atomic
import com.technocreatives.beckon.*
import com.technocreatives.beckon.extensions.scan
import com.technocreatives.beckon.extensions.subscribe
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import no.nordicsemi.android.mesh.MeshBeacon
import no.nordicsemi.android.mesh.UnprovisionedBeacon
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.*

class BeckonMesh(
    context: Context,
    private val beckonClient: BeckonClient,
    private val meshApi: BeckonMeshManagerApi
) {

    val stateSubject = MutableSharedFlow<MeshState>()
    private lateinit var currentState: Atomic<MeshState>

    private suspend fun initState() {
        currentState = Atomic.invoke(Loaded(this, meshApi))
    }

    init {
        runBlocking {
            initState()
        }
    }

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

    suspend fun scanForProvisioning(): Flow<Either<ScanError, List<UnprovisionedScanResult>>> {
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
            .mapZ { it.mapNotNull { it.toUnprovisionedScanResult() } }
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

    internal suspend fun connectForProvisioning(scanResult: UnprovisionedScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult.macAddress, MeshConstants.provisioningDataOutCharacteristic)

    suspend fun connectForProxy(scanResult: ScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult.macAddress, MeshConstants.proxyDataOutCharacteristic)

    private suspend fun meshConnect(
        macAddress: MacAddress,
        characteristic: Characteristic
    ): Either<BeckonError, BeckonDevice> =
        either {
            val descriptor = Descriptor()
            val beckonDevice = beckonClient.connect(macAddress, descriptor).bind()
            val mtu = beckonDevice.requestMtu(MeshConstants.maxMtu).bind()
            beckonDevice.subscribe(characteristic).bind()
            GlobalScope.launch {
                // todo is this the right way?
                with(meshApi) {
                    beckonDevice.handleNotifications(characteristic)
                }
            }
            beckonDevice
        }


    private fun ScanResult.toUnprovisionedScanResult(): UnprovisionedScanResult? {
        return scanRecord?.unprovisionedDeviceUuid()?.let {
            UnprovisionedScanResult(macAddress, name, rssi, it)
        }
    }

    private fun ScanRecord.unprovisionedDeviceUuid(): UUID? {
        val beacon = getMeshBeacon(bytes!!) as? UnprovisionedBeacon

        return if (beacon != null) {
            beacon.uuid
        } else {
            val serviceData: ByteArray? =
                getServiceData(ParcelUuid(MeshConstants.MESH_PROVISIONING_SERVICE_UUID))
            if (serviceData != null) {
                meshApi.getDeviceUuid(serviceData)
            } else {
                null
            }
        }
    }

    fun getMeshBeacon(bytes: ByteArray): MeshBeacon? {
        val beaconData: ByteArray? = meshApi.getMeshBeaconData(bytes)
        return beaconData?.let {
            meshApi.getMeshBeacon(beaconData)
        }
    }
}

@Parcelize
data class UnprovisionedScanResult(
    val macAddress: MacAddress,
    val name: String?,
    val rssi: Int,
    val uuid: UUID
) : Parcelable


class ProvisioningViewModel(val client: BeckonMeshClient, val meshUuid: UUID) {

    suspend fun doProvisioning(scanResult: UnprovisionedScanResult): Either<Any, Any> = either {
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

    suspend fun identify(scanResult: UnprovisionedScanResult): Either<Any, UnprovisionedMeshNode> =
        either {
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
