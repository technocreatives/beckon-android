package com.technocreatives.beckon.mesh

import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.Mtu
import java.util.*

object MeshConstants {
    val MESH_PROVISIONING_SERVICE_UUID: UUID =
        UUID.fromString("00001827-0000-1000-8000-00805F9B34FB")

    /**
     * Mesh provisioning data in characteristic UUID
     */
    val MESH_PROVISIONING_DATA_IN: UUID =
        UUID.fromString("00002ADB-0000-1000-8000-00805F9B34FB")

    /**
     * Mesh provisioning data out characteristic UUID
     */
    val MESH_PROVISIONING_DATA_OUT: UUID =
        UUID.fromString("00002ADC-0000-1000-8000-00805F9B34FB")

    /**
     * Mesh proxy service UUID
     */
    val MESH_PROXY_SERVICE_UUID: UUID = UUID.fromString("00001828-0000-1000-8000-00805F9B34FB")

    /**
     * Mesh proxy data in characteristic UUID
     */
    val MESH_PROXY_DATA_IN: UUID = UUID.fromString("00002ADD-0000-1000-8000-00805F9B34FB")

    /**
     * Mesh proxy data out characteristic UUID
     */
    val MESH_PROXY_DATA_OUT: UUID = UUID.fromString("00002ADE-0000-1000-8000-00805F9B34FB")

    val provisioningDataOutCharacteristic = Characteristic(
        MeshConstants.MESH_PROVISIONING_DATA_OUT,
        MeshConstants.MESH_PROVISIONING_SERVICE_UUID
    )

    val provisioningDataInCharacteristic = Characteristic(
        MeshConstants.MESH_PROVISIONING_DATA_IN,
        MeshConstants.MESH_PROVISIONING_SERVICE_UUID
    )

    val proxyDataInCharacteristic = Characteristic(
        MeshConstants.MESH_PROXY_DATA_IN,
        MeshConstants.MESH_PROXY_SERVICE_UUID
    )

    val proxyDataOutCharacteristic = Characteristic(
        MeshConstants.MESH_PROXY_DATA_OUT,
        MeshConstants.MESH_PROXY_SERVICE_UUID
    )

    val maxMtu = Mtu(517)
}
