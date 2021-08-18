package com.technocreatives.beckon.mesh.extensions

import android.os.ParcelUuid
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.mesh.MeshConstants
import com.technocreatives.beckon.mesh.model.UnprovisionedScanResult
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.UnprovisionedBeacon
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import java.util.*
fun ScanRecord.unprovisionedDeviceUuid(meshApi: MeshManagerApi): UUID? =
    (meshApi.meshBeacon(bytes!!) as? UnprovisionedBeacon)?.uuid ?: run {
        val serviceData: ByteArray? =
            getServiceData(ParcelUuid(MeshConstants.MESH_PROVISIONING_SERVICE_UUID))
        serviceData?.let {
            meshApi.getDeviceUuid(it)
        }
    }

fun ScanResult.toUnprovisionedScanResult(meshApi: MeshManagerApi): UnprovisionedScanResult? =
    scanRecord?.unprovisionedDeviceUuid(meshApi)?.let {
        UnprovisionedScanResult(macAddress, name, rssi, it)
    }
