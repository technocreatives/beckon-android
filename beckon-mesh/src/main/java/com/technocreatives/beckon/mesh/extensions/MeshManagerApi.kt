package com.technocreatives.beckon.mesh.extensions

import android.os.ParcelUuid
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.mesh.ProvisioningError
import kotlinx.coroutines.CompletableDeferred
import no.nordicsemi.android.mesh.MeshBeacon
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.Provisioner
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import timber.log.Timber

fun MeshManagerApi.meshBeacon(bytes: ByteArray): MeshBeacon? =
    getMeshBeaconData(bytes)?.let {
        getMeshBeacon(it)
    }

suspend fun MeshManagerApi.nextAvailableUnicastAddress(unprovisionedMeshNode: UnprovisionedMeshNode): Either<ProvisioningError, Int> {
    val provisioningEmitter =
        CompletableDeferred<Either<ProvisioningError, Int>>()
    meshNetwork!!.let {
        try {
            val elementCount: Int =
                unprovisionedMeshNode.provisioningCapabilities.numberOfElements.toInt()
            val provisioner: Provisioner = it.selectedProvisioner
            val availableUnicast: Int =
                it.nextAvailableUnicastAddress(elementCount, provisioner)
            if (availableUnicast == -1) {
                provisioningEmitter.complete(ProvisioningError.NoAvailableUnicastAddress.left())
            } else {
                it.assignUnicastAddress(availableUnicast)
                provisioningEmitter.complete(availableUnicast.right())
            }
        } catch (ex: IllegalArgumentException) {
            provisioningEmitter.complete(ProvisioningError.NoAllocatedUnicastRange.left())
        }
    }
    return provisioningEmitter.await()
}

fun MeshManagerApi.isNodeInTheMesh(
    scanRecord: ScanRecord,
): Boolean {
    Timber.d("isNodeInTheMesh: ${scanRecord.deviceName}")
    val serviceData = scanRecord.getServiceData(ParcelUuid(MeshManagerApi.MESH_PROXY_UUID))
    val isNodeInTheMesh = isAdvertisingWithNetworkIdentity(serviceData) && networkIdMatches(serviceData)
    Timber.d("isNodeInTheMesh: $isNodeInTheMesh")
    return isNodeInTheMesh
}

suspend fun MeshManagerApi.isProxyDevice(
    scanRecord: ScanRecord,
    meshNode: ProvisionedMeshNode,
    onFound: suspend () -> Unit
): Boolean {
    val serviceData = scanRecord.getServiceData(ParcelUuid(MeshManagerApi.MESH_PROXY_UUID))
    Timber.d("findProxyDeviceAndStopScan, serviceData: ${serviceData?.size} ${serviceData?.toList()}")
    if (serviceData != null) {
        val isAdvertisedWithNodeIdentity = isAdvertisedWithNodeIdentity(serviceData)
        Timber.d("isAdvertisedWithNodeIdentity: $isAdvertisedWithNodeIdentity")
        if (isAdvertisedWithNodeIdentity) {
            val node: ProvisionedMeshNode = meshNode
            val nodeIdentityMatches = nodeIdentityMatches(node, serviceData)
            Timber.d("nodeIdentityMatches: $isAdvertisedWithNodeIdentity")
            if (nodeIdentityMatches) {
                onFound()
                return true
            }
        }
    }
    return false
}