package com.technocreatives.beckon.mesh

import com.technocreatives.beckon.Characteristic
import no.nordicsemi.android.mesh.MeshNetwork

enum class ConnectionPhase {
    Proxy,
    Provisioning;

    fun dataOutCharacteristic(): Characteristic =
        when (this) {
            Provisioning -> MeshConstants.provisioningDataOutCharacteristic
            Proxy -> MeshConstants.proxyDataOutCharacteristic
        }

    fun dataInCharacteristic(): Characteristic =
        when(this) {
            Provisioning -> MeshConstants.provisioningDataInCharacteristic
            Proxy -> MeshConstants.proxyDataInCharacteristic
        }
}