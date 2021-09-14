package com.technocreatives.beckon.mesh.extensions

import android.os.ParcelUuid
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.mesh.NoAllocatedUnicastRange
import com.technocreatives.beckon.mesh.NoAvailableUnicastAddress
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
                provisioningEmitter.complete(NoAvailableUnicastAddress.left())
            } else {
                it.assignUnicastAddress(availableUnicast)
                provisioningEmitter.complete(availableUnicast.right())
            }
        } catch (ex: IllegalArgumentException) {
            provisioningEmitter.complete(NoAllocatedUnicastRange.left())
        }
    }
    return provisioningEmitter.await()
}

fun MeshManagerApi.isNodeInTheMesh(
    scanRecord: ScanRecord,
): Boolean {
    Timber.d("isNodeInTheMesh: ${scanRecord.deviceName}")
    val serviceData = scanRecord.getServiceData(ParcelUuid(MeshManagerApi.MESH_PROXY_UUID))
    val networkIdMatches = networkIdMatches(serviceData)
    val isAdvertisingWithNetworkIdentity = isAdvertisingWithNetworkIdentity(serviceData)
//    val isAdvertisedWithNodeIdentity = isAdvertisedWithNodeIdentity(serviceData)
    Timber.d("${scanRecord.deviceName} -> networkIdMatches=$networkIdMatches, isAdvertisingWithNetworkIdentity=$isAdvertisingWithNetworkIdentity")
    return networkIdMatches && isAdvertisingWithNetworkIdentity
}


// TODO Look into the expected behaviour of a node after provisioning
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
            val nodeIdentityMatches = nodeIdentityMatches(meshNode, serviceData)
            val isAdvertisingWithNetworkIdentity = isAdvertisingWithNetworkIdentity(serviceData)
            Timber.d("${scanRecord.deviceName} -> isAdvertisingWithNetworkIdentity=$isAdvertisingWithNetworkIdentity, nodeIdentityMatches=$nodeIdentityMatches")
            if (nodeIdentityMatches || isAdvertisingWithNetworkIdentity) {
                onFound()
                return true
            }
        }
    }
    return false
}