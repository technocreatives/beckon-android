package com.technocreatives.beckon.mesh

import android.os.ParcelUuid
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.mesh.utils.tap
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapEither
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.single
import no.nordicsemi.android.mesh.*
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ConfigCompositionDataGet
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import timber.log.Timber
import java.util.*


class ProvisioningPhase(private val meshApi: BeckonMeshManagerApi) {

    private val inviteEmitter =
        CompletableDeferred<Either<ProvisioningFailed, UnprovisionedMeshNode>>()

    private val provisioningEmitter =
        CompletableDeferred<Either<ProvisioningFailed, ProvisionedMeshNode>>()

    private val exchangeKeysEmitter =
        CompletableDeferred<Either<Unit, ProvisionedMeshNode>>()

    suspend fun completeExchangeKeys(node: ProvisionedMeshNode) {
        exchangeKeysEmitter.complete(node.right())
    }

    suspend fun completeProvisioning(scanResult: ScanResult): Either<Any, BeckonDevice> = either {
        val beckonDevice = connect(scanResult).bind()
        val unprovisionedMeshNode = identify(scanResult).bind()
        val provisionedMeshNode = provisionDevice(unprovisionedMeshNode).bind()
        val proxyDevice = scanAndConnect(beckonDevice, provisionedMeshNode).bind()
        exchangeKeys(beckonDevice, provisionedMeshNode).bind()
        proxyDevice
    }

    suspend fun connect(scanResult: ScanResult): Either<BeckonError, BeckonDevice> {
        return meshApi.connectForProvisioning(scanResult)
            .tap {
                meshApi.beckonDevice = it
            }
    }

    suspend fun identify(scanResult: ScanResult): Either<ProvisioningFailed, UnprovisionedMeshNode> {

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

    suspend fun provisionDevice(unprovisionedMeshNode: UnprovisionedMeshNode): Either<ProvisioningFailed, ProvisionedMeshNode> {
        Timber.d("PROVISION ME ${unprovisionedMeshNode.deviceUuid}")

        meshApi.meshNetwork?.let {
            try {
                val elementCount: Int =
                    unprovisionedMeshNode.provisioningCapabilities.numberOfElements.toInt()
                val provisioner: Provisioner = it.selectedProvisioner
                val unicast: Int = it.nextAvailableUnicastAddress(elementCount, provisioner)
                it.assignUnicastAddress(unicast)
            } catch (ex: IllegalArgumentException) {
                Timber.e(ex, "provisionDevice")
            }
        }

        meshApi.startProvisioning(unprovisionedMeshNode)


        return provisioningEmitter.await()
    }

    suspend fun scanAndConnect(
        beckonDevice: BeckonDevice,
        meshNode: ProvisionedMeshNode
    ): Either<BeckonError, BeckonDevice> {
        beckonDevice.disconnect()
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

        Timber.d("findProxyDeviceAndStopScan $scanRecord")
        val serviceData = getServiceData(scanRecord, MeshManagerApi.MESH_PROXY_UUID)
        Timber.d("findProxyDeviceAndStopScan, serviceData: ${serviceData?.size} ${serviceData?.toList()}")
        if (serviceData != null) {
            val b = meshApi.isAdvertisedWithNodeIdentity(serviceData)
            Timber.d("isAdvertisedWithNodeIdentity: $b")
            if (b) {
                val node: ProvisionedMeshNode = meshNode
                val c = meshApi.nodeIdentityMatches(node, serviceData)
                Timber.d("nodeIdentityMatches: $b")
                if (c) {
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
            val failed = ProvisioningFailed(meshNode, state, data).left()
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

}

data class ProvisioningFailed(
    val node: UnprovisionedMeshNode?,
    val state: ProvisioningState.States?,
    val data: ByteArray?
) : MeshError
