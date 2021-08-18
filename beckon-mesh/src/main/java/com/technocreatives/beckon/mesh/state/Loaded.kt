package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.BeckonMeshManagerApi

class Loaded(beckonMesh: BeckonMesh, meshApi: BeckonMeshManagerApi) :
    MeshState(beckonMesh, meshApi) {
    suspend fun startProvisioning(): Provisioning {
        val appKey = meshApi.meshNetwork!!.getAppKey(0)!!
        val provisioning = Provisioning(beckonMesh, meshApi, appKey)
        beckonMesh.updateState(provisioning)
        return provisioning
    }

    suspend fun connect(scanResult: ScanResult): Either<Any, Connected> = either {
        val beckonDevice = beckonMesh.connectForProxy(scanResult).bind()
        val connected = Connected(beckonMesh, meshApi, beckonDevice)
        beckonMesh.updateState(connected)
        connected
    }
}