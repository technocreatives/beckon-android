package com.technocreatives.beckon.mesh

import com.technocreatives.beckon.Characteristic
import no.nordicsemi.android.mesh.MeshNetwork

sealed class MeshState {
    object Unloaded : MeshState()
    data class Loaded(val mesh: MeshNetwork) : MeshState()
}

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