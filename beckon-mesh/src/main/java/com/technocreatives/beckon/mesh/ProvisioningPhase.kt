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
import com.technocreatives.beckon.mesh.callbacks.MessageStatus
import com.technocreatives.beckon.mesh.utils.tap
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapEither
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import no.nordicsemi.android.mesh.*
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.*
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import timber.log.Timber
import java.util.*

class ProvisioningPhase(
    private val meshApi: BeckonMeshManagerApi,
    private val appKey: ApplicationKey
) {

    private val inviteEmitter =
        CompletableDeferred<Either<ProvisioningError.ProvisioningFailed, UnprovisionedMeshNode>>()

    private val provisioningEmitter =
        CompletableDeferred<Either<ProvisioningError, ProvisionedMeshNode>>()

    private val exchangeKeysEmitter =
        CompletableDeferred<Either<Unit, ProvisionedMeshNode>>()

    private var unicast = -1

    // todo typesafe scan result
    suspend fun completeProvisioning(scanResult: ScanResult): Either<Any, BeckonDevice> = either {
        val beckonDevice = connect(scanResult).bind()
        val unprovisionedMeshNode = identify(scanResult).bind()
        val provisionedMeshNode = startProvisioning(unprovisionedMeshNode).bind()
        beckonDevice.disconnect().bind()
        val proxyDevice = scanAndConnect(provisionedMeshNode).bind()
        exchangeKeys(proxyDevice, provisionedMeshNode).bind()
        proxyDevice
    }

    // disconnect device if needed
    // change state of MeshManagerApi
    suspend fun cancel(): Unit = TODO()

    suspend fun connect(scanResult: ScanResult): Either<BeckonError, BeckonDevice> {
        return meshApi.connectForProvisioning(scanResult)
            .tap {
                meshApi.beckonDevice = it
            }
    }

    suspend fun identify(scanResult: ScanResult): Either<ProvisioningError.ProvisioningFailed, UnprovisionedMeshNode> {

        val scanRecord: ScanRecord = scanResult.scanRecord!!

        val beacon = getMeshBeacon(scanRecord.bytes!!) as? UnprovisionedBeacon

        if (beacon != null) {
            meshApi.identifyNode(beacon.uuid, 5)
            Timber.d("identify with uuid from beaconData: ${beacon.uuid}")
        } else {
            val serviceData: ByteArray? =
                getServiceData(scanResult, MeshConstants.MESH_SERVICE_PROVISIONING_UUID)
            if (serviceData != null) {
                val uuid: UUID = meshApi.getDeviceUuid(serviceData)
                Timber.d("identify with uuid from service: $uuid")
                meshApi.identifyNode(
                    uuid,
                    5
                )
            }
        }

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
        return meshApi.scanForProvisioning()
            .mapZ {
                it.firstOrNull {
                    findProxyDeviceAndStopScan(it.scanRecord!!, meshNode)
                }
            }.filterZ { it != null }
            .mapEither { meshApi.connectForProxy(it!!) }
            .single()
            .tap {
                meshApi.beckonDevice = it
            }
    }


    // todo determine when it failed
    private suspend fun exchangeKeys(
        beckonDevice: BeckonDevice,
        node: ProvisionedMeshNode
    ): Either<Unit, ProvisionedMeshNode> {
        val compositionDataGet = ConfigCompositionDataGet()
        meshApi.createMeshPdu(node.unicastAddress, compositionDataGet)
        return exchangeKeysEmitter.await().tap {
            meshApi.setConnected(beckonDevice)
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
        val serviceData = getServiceData(scanRecord, MeshManagerApi.MESH_PROXY_UUID)
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
                    meshApi.stopScan()
                    return true
                }
            }
        }
        return false
    }

    private fun getServiceData(result: ScanRecord, serviceUuid: UUID): ByteArray? {
        return result.getServiceData(ParcelUuid(serviceUuid))
    }

    val provisioningStatusCallbacks = object : MeshProvisioningStatusCallbacks {

        var accumulatedStates = emptyList<ProvisioningState.States>()

        override fun onProvisioningStateChanged(
            meshNode: UnprovisionedMeshNode,
            state: ProvisioningState.States,
            data: ByteArray?
        ) {
            Timber.d("onProvisioningStateChanged: ${meshNode?.nodeName} $state, data: ${data?.size}")
            // todo check provisioning failed state
            accumulatedStates = accumulatedStates + state
            when (state) {
                ProvisioningState.States.PROVISIONING_CAPABILITIES -> {
                    inviteEmitter.complete(meshNode.right())
                }
                else -> {

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
        }

    }

    fun List<ProvisioningState.States>.isInviting(): Boolean {
        // todo correct this formula
        return this.size <= 1
    }

    @SuppressLint("RestrictedApi")
    fun handleMessageReceived(message: MessageStatus.MeshMessageReceived) {
        if (message.src != unicast) {
            Timber.w("the received message src is not our current provisioning device")
            return
        }
        val meshNetwork = meshApi.meshNetwork!!
        val node = meshNetwork.getNode(message.src)!!
        when (message.meshMessage.opCode) {
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
                val status = message.meshMessage as ConfigDefaultTtlStatus
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
                val status = message.meshMessage as ConfigAppKeyStatus
                Timber.d("onMessageReceived CONFIG_APPKEY_STATUS: ${status.isSuccessful}")
                exchangeKeysEmitter.complete(node.right())
            }
            else -> {
                exchangeKeysEmitter.complete(Unit.left())
                Timber.d("onMessageReceived other message when provisioning $message")
            }
        }
    }
}

sealed class ProvisioningError : MeshError {

    data class ProvisioningFailed(
        val node: UnprovisionedMeshNode?,
        val state: ProvisioningState.States?,
        val data: ByteArray?
    ) : ProvisioningError()

    object NoAvailableUnicastAddress : ProvisioningError()
    object NoAllocatedUnicastRange : ProvisioningError()
}
