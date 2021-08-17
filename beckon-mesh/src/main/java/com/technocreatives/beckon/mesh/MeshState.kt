package com.technocreatives.beckon.mesh


import android.annotation.SuppressLint
import android.os.ParcelUuid
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.extensions.getMaximumPacketSize
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.callbacks.AbstractMessageStatusCallbacks
import com.technocreatives.beckon.mesh.utils.tap
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapEither
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import no.nordicsemi.android.mesh.*
import no.nordicsemi.android.mesh.models.VendorModel
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.*
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import timber.log.Timber
import java.util.*

sealed interface MeshError

data class IllegalMeshStateError(val state: MeshState) : MeshError, Exception()

data class MeshLoadFailedError(val error: String) : MeshError

sealed class ProvisioningError : MeshError {

    data class ProvisioningFailed(
        val node: UnprovisionedMeshNode?,
        val state: ProvisioningState.States?,
        val data: ByteArray?
    ) : ProvisioningError()

    object NoAvailableUnicastAddress : ProvisioningError()
    object NoAllocatedUnicastRange : ProvisioningError()
}

sealed class MeshState(val beckonMesh: BeckonMesh, val meshApi: BeckonMeshManagerApi) {

    fun isValid(): Boolean = TODO()
}

class Loaded(beckonMesh: BeckonMesh, meshApi: BeckonMeshManagerApi) :
    MeshState(beckonMesh, meshApi) {
    suspend fun startProvisioning(): Provisioning {
        val appKey = meshApi.meshNetwork!!.getAppKey(0)!!
        val provisioning = Provisioning(beckonMesh, meshApi, appKey)
        beckonMesh.updateState(provisioning)
        return provisioning
    }

    suspend fun connect(scanResult: ScanResult): Either<Any, Connected> = either {
        val beckonDevice = beckonMesh.connectForProxy(scanResult).bind()
        val connected = Connected(beckonMesh, meshApi, beckonDevice)
        beckonMesh.updateState(connected)
        connected
    }
}

// provisioning phase
class Provisioning(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val appKey: ApplicationKey
) : MeshState(beckonMesh, meshApi) {

    private val inviteEmitter =
        CompletableDeferred<Either<ProvisioningError.ProvisioningFailed, UnprovisionedMeshNode>>()

    private val provisioningEmitter =
        CompletableDeferred<Either<ProvisioningError, ProvisionedMeshNode>>()

    private val exchangeKeysEmitter =
        CompletableDeferred<Either<Unit, ProvisionedMeshNode>>()

    private var unicast = -1

    private val provisioningStatusCallbacks = object : MeshProvisioningStatusCallbacks {

        var accumulatedStates = emptyList<ProvisioningState.States>()

        override fun onProvisioningStateChanged(
            meshNode: UnprovisionedMeshNode,
            state: ProvisioningState.States,
            data: ByteArray?
        ) {
            Timber.d("onProvisioningStateChanged: ${meshNode.nodeName} $state, data: ${data?.size}")
            // todo check provisioning failed state
            accumulatedStates = accumulatedStates + state
            when (state) {
                ProvisioningState.States.PROVISIONING_CAPABILITIES -> {
                    inviteEmitter.complete(meshNode.right())
                }
                else -> {
                    Timber.d("Unprocess state: $state")
                }
            }
        }

        override fun onProvisioningFailed(
            meshNode: UnprovisionedMeshNode?,
            state: ProvisioningState.States?,
            data: ByteArray?
        ) {
            val failed = ProvisioningError.ProvisioningFailed(meshNode, state, data).left()
            if (accumulatedStates.isInviting()) {
                inviteEmitter.complete(failed)
            } else {
                provisioningEmitter.complete(failed)
            }
        }

        override fun onProvisioningCompleted(
            meshNode: ProvisionedMeshNode,
            state: ProvisioningState.States,
            data: ByteArray?
        ) {
            Timber.d("onProvisioningCompleted: ${meshNode.nodeName} $state");
            provisioningEmitter.complete(meshNode.right())
            GlobalScope.launch {
                meshApi.updateNodes()
            }
        }
    }

    private var beckonDevice: BeckonDevice? = null

    init {
        meshApi.setProvisioningStatusCallbacks(provisioningStatusCallbacks)
        meshApi.setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
            override fun onMeshPduCreated(pdu: ByteArray) {
                Timber.d("sendPdu - onMeshPduCreated - ${pdu.size}")
                beckonDevice?.let { bd ->
                    GlobalScope.launch {
                        with(meshApi) {
                            bd.sendPdu(pdu, MeshConstants.proxyDataInCharacteristic).fold(
                                { Timber.w("SendPdu error: $it") },
                                { Timber.d("sendPdu success") }
                            )
                        }
                    }
                }
            }

            override fun onNetworkUpdated(meshNetwork: MeshNetwork?) {
            }

            override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode, pdu: ByteArray) {
                Timber.d("sendPdu - sendProvisioningPdu - ${pdu.size}")
                beckonDevice?.let { bd ->
                    GlobalScope.launch {
                        with(meshApi) {
                            bd.sendPdu(pdu, MeshConstants.provisioningDataInCharacteristic).fold(
                                { Timber.w("SendPdu error: $it") },
                                { Timber.d("sendPdu success") }
                            )
                        }
                    }
                }
            }

            override fun getMtu(): Int {
                return beckonDevice?.getMaximumPacketSize() ?: 66
            }
        })

        meshApi.setMeshStatusCallbacks(object : AbstractMessageStatusCallbacks(meshApi) {
            override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
                super.onMeshMessageReceived(src, meshMessage)
                handleMessageReceived(src, meshMessage)
            }
        })
    }

    // disconnect device if needed
    // change state of MeshManagerApi
    suspend fun cancel(): Either<Throwable, Unit> = either {
        beckonDevice?.disconnect()?.bind()
        beckonMesh.updateState(Loaded(beckonMesh, meshApi))
    }

    suspend fun connect(scanResult: UnprovisionedScanResult): Either<BeckonError, BeckonDevice> {
        return beckonMesh.connectForProvisioning(scanResult)
            .tap {
                beckonDevice = it
            }
    }

    suspend fun identify(scanResult: UnprovisionedScanResult): Either<ProvisioningError.ProvisioningFailed, UnprovisionedMeshNode> {


        meshApi.identifyNode(
            scanResult.uuid,
            5
        )

        return inviteEmitter.await()
    }

    suspend fun startProvisioning(unprovisionedMeshNode: UnprovisionedMeshNode): Either<ProvisioningError, ProvisionedMeshNode> {
        Timber.d("startProvisioning ${unprovisionedMeshNode.deviceUuid}")

        meshApi.meshNetwork!!.let {
            try {
                val elementCount: Int =
                    unprovisionedMeshNode.provisioningCapabilities.numberOfElements.toInt()
                val provisioner: Provisioner = it.selectedProvisioner
                val availableUnicast: Int =
                    it.nextAvailableUnicastAddress(elementCount, provisioner)
                if (availableUnicast == -1) {
                    provisioningEmitter.complete(ProvisioningError.NoAvailableUnicastAddress.left())
                } else {
                    unicast = availableUnicast
                    it.assignUnicastAddress(availableUnicast)
                }
            } catch (ex: IllegalArgumentException) {
                provisioningEmitter.complete(ProvisioningError.NoAllocatedUnicastRange.left())
            }
        }

        meshApi.startProvisioning(unprovisionedMeshNode)
        return provisioningEmitter.await()
    }

    suspend fun scanAndConnect(
        meshNode: ProvisionedMeshNode
    ): Either<BeckonError, BeckonDevice> {
        return beckonMesh.scanForProxy()
            .mapZ {
                it.firstOrNull {
                    findProxyDeviceAndStopScan(it.scanRecord!!, meshNode)
                }
            }.filterZ { it != null }
            .mapEither { beckonMesh.connectForProxy(it!!) }
            .first()
            .tap {
                beckonDevice = it
            }
    }

    suspend fun exchangeKeys(
        beckonDevice: BeckonDevice,
        node: ProvisionedMeshNode
    ): Either<Unit, ProvisionedMeshNode> {
        val compositionDataGet = ConfigCompositionDataGet()
        meshApi.createMeshPdu(node.unicastAddress, compositionDataGet)
        return exchangeKeysEmitter.await().tap {
            // todo finished provisioning
            beckonMesh.updateState(Connected(beckonMesh, meshApi, beckonDevice))
        }
    }

    private fun getServiceData(result: ScanResult, serviceUuid: UUID): ByteArray? {
        val scanRecord: ScanRecord? = result.scanRecord
        return scanRecord?.getServiceData(ParcelUuid(serviceUuid))
    }

    private fun getMeshBeacon(bytes: ByteArray): MeshBeacon? {
        val beaconData: ByteArray? = meshApi.getMeshBeaconData(bytes)
        return beaconData?.let {
            meshApi.getMeshBeacon(beaconData)
        }
    }

    // todo timeout
    private suspend fun findProxyDeviceAndStopScan(
        scanRecord: ScanRecord,
        meshNode: ProvisionedMeshNode
    ): Boolean {
        val serviceData = scanRecord.getServiceData(ParcelUuid(MeshManagerApi.MESH_PROXY_UUID))
        Timber.d("findProxyDeviceAndStopScan, serviceData: ${serviceData?.size} ${serviceData?.toList()}")
        if (serviceData != null) {
            val isAdvertisedWithNodeIdentity = meshApi.isAdvertisedWithNodeIdentity(serviceData)
            Timber.d("isAdvertisedWithNodeIdentity: $isAdvertisedWithNodeIdentity")
            if (isAdvertisedWithNodeIdentity) {
                val node: ProvisionedMeshNode = meshNode
                val nodeIdentityMatches = meshApi.nodeIdentityMatches(node, serviceData)
                Timber.d("nodeIdentityMatches: $isAdvertisedWithNodeIdentity")
                if (nodeIdentityMatches) {
                    // todo better way to stop scan
                    beckonMesh.stopScan()
                    return true
                }
            }
        }
        return false
    }


    fun List<ProvisioningState.States>.isInviting(): Boolean {
        // todo correct this formula
        return this.size <= 1
    }

    @SuppressLint("RestrictedApi")
    fun handleMessageReceived(src: Int, meshMessage: MeshMessage) {
        if (src != unicast) {
            Timber.w("the received message src is not our current provisioning device")
            return
        }
        val meshNetwork = meshApi.meshNetwork!!
        val node = meshNetwork.getNode(src)!!
        when (meshMessage.opCode) {
            ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt() -> {
                Timber.d("onMessageReceived CONFIG_COMPOSITION_DATA_STATUS:")
                GlobalScope.launch {
                    // TODO delay
                    delay(500)

                    val configDefaultTtlGet = ConfigDefaultTtlGet()
                    meshApi.createMeshPdu(
                        node.unicastAddress,
                        configDefaultTtlGet
                    )
                }
            }
            ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS -> {
                val status = meshMessage as ConfigDefaultTtlStatus
                Timber.d("onMessageReceived CONFIG_DEFAULT_TTL_STATUS: $status")
                GlobalScope.launch {
                    // TODO delay
                    delay(1500)
                    val networkTransmitSet = ConfigNetworkTransmitSet(2, 1)
                    meshApi.createMeshPdu(
                        node.unicastAddress,
                        networkTransmitSet
                    )
                }
            }
            ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS -> {
                Timber.d("onMessageReceived CONFIG_NETWORK_TRANSMIT_STATUS")
                GlobalScope.launch {
                    // TODO delay global
                    delay(1500)
                    val index: Int = node.addedNetKeys!!.get(0)!!.index
                    val networkKey: NetworkKey = meshNetwork.netKeys[index]
                    val configAppKeyAdd = ConfigAppKeyAdd(networkKey, appKey)
                    meshApi.createMeshPdu(
                        node.unicastAddress,
                        configAppKeyAdd
                    )
                }
            }
            ConfigMessageOpCodes.CONFIG_APPKEY_STATUS -> {
                val status = meshMessage as ConfigAppKeyStatus
                Timber.d("onMessageReceived CONFIG_APPKEY_STATUS: ${status.isSuccessful}")
                exchangeKeysEmitter.complete(node.right())
            }
            else -> {
                exchangeKeysEmitter.complete(Unit.left())
                Timber.d("onMessageReceived other message when provisioning $src, $meshMessage")
            }
        }
    }
}

class Connected(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val beckonDevice: BeckonDevice
) : MeshState(beckonMesh, meshApi) {

    init {

        meshApi.setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {})
        meshApi.setMeshStatusCallbacks(object : AbstractMessageStatusCallbacks(meshApi) {})
    }

    suspend fun disconnect(): Either<Any, Loaded> = either {
        beckonDevice.disconnect().bind()
        val loaded = Loaded(beckonMesh, meshApi)
        beckonMesh.updateState(loaded)
        loaded
    }

    suspend fun bindAppKeyToVendorModel(): Either<Any, Unit> = TODO()

    suspend fun sendVendorModelMessage(
        appKey: ApplicationKey,
        vendorModel: VendorModel,
        opCode: ByteArray
    ): Either<Any, Unit> = TODO()

    // all other features
    init {
        beckonDevice.connectionStates()
    }

}
