package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import arrow.core.continuations.either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.BeckonMeshManagerApi
import com.technocreatives.beckon.mesh.ConnectionConfig
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.mesh.MeshNetwork

class Loaded(beckonMesh: BeckonMesh, meshApi: BeckonMeshManagerApi) :
    MeshState(beckonMesh, meshApi) {

    init {
        meshApi.setMeshManagerCallbacks(object: AbstractMeshManagerCallbacks() {
            override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
                runBlocking {
                    meshApi.updateNetwork()
                }
            }
        })
    }
    override suspend fun isValid(): Boolean = beckonMesh.isCurrentState<Loaded>()

    suspend fun startProvisioning(beckonDevice: BeckonDevice): Provisioning {
        val provisioning = Provisioning(beckonMesh, meshApi, beckonDevice)
        beckonMesh.updateState(provisioning)
        return provisioning
    }

    suspend fun connect(macAddress: MacAddress, config: ConnectionConfig): Either<Any, Connected> = either {
        val beckonDevice = beckonMesh.connectForProxy(macAddress, config).bind()
        val connected = beckonMesh.createConnectedState(beckonDevice)
        beckonMesh.updateState(connected)
        connected
    }

    suspend fun connect(beckonDevice: BeckonDevice): Connected {
        val connected = beckonMesh.createConnectedState(beckonDevice)
        beckonMesh.updateState(connected)
        return connected
    }
}