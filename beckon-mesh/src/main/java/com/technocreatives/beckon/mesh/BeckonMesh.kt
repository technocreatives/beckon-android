package com.technocreatives.beckon.mesh

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.*
import com.technocreatives.beckon.extensions.subscribe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface BeckonMesh {

    companion object {
        val provisioningDataOutCharacteristic = Characteristic(
            MeshConstants.MESH_PROVISIONING_DATA_OUT,
            MeshConstants.MESH_SERVICE_PROVISIONING_UUID
        )
        val provisioningInCharacteristic = Characteristic(
            MeshConstants.MESH_PROVISIONING_DATA_IN,
            MeshConstants.MESH_SERVICE_PROVISIONING_UUID
        )
        val proxyDataOutCharacteristic = Characteristic(
            MeshConstants.MESH_PROXY_DATA_OUT,
            MeshConstants.MESH_PROXY_UUID
        )

        val maxMtu = Mtu(517)
    }

    suspend fun BeckonClient.connectForProvisioning(scanResult: ScanResult): Either<Any, BeckonDevice> =
        either {
            val descriptor = Descriptor()
            val beckonDevice = connect(scanResult, descriptor).bind()
            val mtu = beckonDevice.requestMtu(maxMtu).bind()
            beckonDevice.subscribe(provisioningDataOutCharacteristic).bind()
            coroutineScope {
                async {
                }
            }
            beckonDevice
        }
}
