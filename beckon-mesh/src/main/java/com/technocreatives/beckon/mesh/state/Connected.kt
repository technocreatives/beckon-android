package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.right
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.BeckonMeshManagerApi
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.callbacks.AbstractMessageStatusCallbacks
import com.technocreatives.beckon.mesh.model.AppKey
import com.technocreatives.beckon.mesh.model.Node
import com.technocreatives.beckon.mesh.model.VendorModel
import no.nordicsemi.android.mesh.transport.VendorModelMessageAcked

class Connected(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val beckonDevice: BeckonDevice
) : MeshState(beckonMesh, meshApi) {

    init {

        meshApi.setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {})
        meshApi.setMeshStatusCallbacks(object : AbstractMessageStatusCallbacks(meshApi) {})
    }

    suspend fun disconnect(): Either<Any, Loaded> = either {
        beckonDevice.disconnect().bind()
        val loaded = Loaded(beckonMesh, meshApi)
        beckonMesh.updateState(loaded)
        loaded
    }

    suspend fun bindAppKeyToVendorModel(): Either<Any, Unit> {

        TODO()
    }

    suspend fun sendVendorModelMessageAck(
        node: Node,
        appKey: AppKey,
        vendorModel: VendorModel,
        opCode: Int,
        parameters: ByteArray
    ): Either<Any, Unit> {
        val message = VendorModelMessageAcked(
            appKey.applicationKey,
            vendorModel.modelId,
            vendorModel.companyIdentifier,
            opCode,
            parameters
        )
        meshApi.createMeshPdu(node.unicastAddress, message)
        return Unit.right()
    }

    // all other features
    init {
        beckonDevice.connectionStates()
    }

}