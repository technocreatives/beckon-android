package com.technocreatives.beckon.mesh.extensions

import android.os.ParcelUuid
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.mesh.NoAllocatedUnicastRange
import com.technocreatives.beckon.mesh.NoAvailableUnicastAddress
import com.technocreatives.beckon.mesh.ProvisioningError
import com.technocreatives.beckon.mesh.data.NetworkId
import com.technocreatives.beckon.mesh.data.netKeys
import com.technocreatives.beckon.mesh.data.transform
import com.technocreatives.beckon.mesh.model.MeshScanResult
import com.technocreatives.beckon.mesh.toHex
import kotlinx.coroutines.CompletableDeferred
import no.nordicsemi.android.mesh.MeshBeacon
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.NetworkKey
import no.nordicsemi.android.mesh.Provisioner
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.mesh.utils.MeshAddress
import no.nordicsemi.android.mesh.utils.SecureUtils
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

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
    val record = scanRecord.transform()
    Timber.e("NetworkID in Hex: ${record.networkId?.value?.toHex()}")
    val networkIds = meshNetwork!!.netKeys().map { it.networkId() }.joinToString(separator = "; ") { it.value.toHex() }
    Timber.d("NetworkId from meshData: $networkIds")
    record.networkId?.let {
        Timber.d("Match with meshdata ${meshNetwork!!.transform().isNetworkIdMatch(it)}")
    }
    return record.networkId != null && networkIdMatches(record.networkId)
}

fun MeshManagerApi.networkIdMatches(id: NetworkId): Boolean =
    meshNetwork!!.netKeys.any { it.isMatch(id) }

fun NetworkKey.isMatch(id: NetworkId): Boolean =
    transform().isNetworkIdMatch(id)

fun MeshManagerApi.isNodeInTheMesh(
    scanRecord: ScanRecord,
    address: Int,
): Boolean {
    Timber.d("isNodeInTheMesh: ${scanRecord.deviceName}")
    val serviceData = scanRecord.getServiceData(ParcelUuid(MeshManagerApi.MESH_PROXY_UUID))
    val isAdvertisedWithNodeIdentity = isAdvertisedWithNodeIdentity(serviceData)
    return if (isAdvertisedWithNodeIdentity) {
        val networkIdMatches = nodeIdentityMatches(serviceData, address)
        Timber.d("${scanRecord.deviceName} -> networkIdMatches=$networkIdMatches, isAdvertisedWithNodeIdentity=$isAdvertisedWithNodeIdentity")
        networkIdMatches
    } else {
        false
    }
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

fun MeshManagerApi.nodeIdentityMatches(serviceData: ByteArray?, unicastAddress: Int): Boolean {
    val advertisedHash: ByteArray = getAdvertisedHash(serviceData) ?: return false
    //If there is no advertised hash return false as this is used to match against the generated hash

    //If there is no advertised random return false as this is used to generate the hash to match against the advertised
    val random: ByteArray = try {
        getAdvertisedRandom(serviceData) ?: return false
    } catch (ex: Exception) {
        Timber.e(ex, "getAdvertisedRandom exception ${serviceData?.toHex()}")
        return false
    }
    for (key: NetworkKey in meshNetwork!!.netKeys) {
        if (Arrays.equals(
                advertisedHash,
                SecureUtils.calculateHash(
                    key.identityKey,
                    random,
                    MeshAddress.addressIntToBytes(unicastAddress)
                )
            ) ||
            key.oldIdentityKey != null &&
            Arrays.equals(
                advertisedHash,
                SecureUtils.calculateHash(
                    key.oldIdentityKey,
                    random,
                    MeshAddress.addressIntToBytes(unicastAddress)
                )
            )
        ) return true
    }
    return false
}

fun ScanRecord.transform(): MeshScanResult {
    val serviceData = getServiceData(ParcelUuid(MeshManagerApi.MESH_PROXY_UUID))
    val networkId = serviceData?.getAdvertisedNetworkId()?.let { NetworkId(it) }
    return MeshScanResult(this, networkId)
}

fun isAdvertisingWithNetworkIdentity(serviceData: ByteArray?): Boolean {
    return serviceData != null && serviceData.size == 9 && serviceData[ADVERTISED_NETWORK_ID_OFFSET - 1].toInt() == ADVERTISEMENT_TYPE_NETWORK_ID
}

/**
 * Returns the advertised network identity
 *
 * @param serviceData advertised service data
 * @return returns the advertised network identity
 */
private fun ByteArray.getAdvertisedNetworkId(): ByteArray? {
    if (!isAdvertisingWithNetworkIdentity(this)) return null
    val advertisedNetworkID =
        ByteBuffer.allocate(ADVERTISED_NETWORK_ID_LENGTH).order(
            ByteOrder.BIG_ENDIAN
        )
    advertisedNetworkID.put(
        this,
        ADVERTISED_NETWORK_ID_OFFSET,
        ADVERTISED_HASH_LENGTH
    )
    return advertisedNetworkID.array()
}

private const val ADVERTISEMENT_TYPE_NETWORK_ID = 0x00
private const val ADVERTISEMENT_TYPE_NODE_IDENTITY = 0x01

private val ADVERTISED_HASH_OFFSET: Int =
    1 // Offset of the hash contained in the advertisement service data

private val ADVERTISED_HASH_LENGTH: Int =
    8 // Length of the hash contained in the advertisement service data

private val ADVERTISED_RANDOM_OFFSET: Int =
    9 // Offset of the hash contained in the advertisement service data

private const val ADVERTISED_RANDOM_LENGTH =
    8 //Length of the hash contained in the advertisement service data

private const val ADVERTISED_NETWORK_ID_OFFSET =
    1 //Offset of the network id contained in the advertisement service data
private const val ADVERTISED_NETWORK_ID_LENGTH =
    8 //Length of the network id contained in the advertisement service data


private fun getAdvertisedHash(serviceData: ByteArray?): ByteArray? {
    if (serviceData == null) return null
    val expectedBufferHash = ByteBuffer.allocate(ADVERTISED_HASH_LENGTH).order(
        ByteOrder.BIG_ENDIAN
    )
    expectedBufferHash.put(
        serviceData,
        ADVERTISED_HASH_OFFSET,
        ADVERTISED_HASH_LENGTH
    )
    return expectedBufferHash.array()
}


private fun getAdvertisedRandom(serviceData: ByteArray?): ByteArray? {
    if (serviceData == null || serviceData.size <= ADVERTISED_RANDOM_LENGTH) return null
    val expectedBufferHash = ByteBuffer.allocate(ADVERTISED_RANDOM_LENGTH).order(
        ByteOrder.BIG_ENDIAN
    )
    expectedBufferHash.put(
        serviceData,
        ADVERTISED_RANDOM_OFFSET,
        ADVERTISED_RANDOM_LENGTH
    )
    return expectedBufferHash.array()
}