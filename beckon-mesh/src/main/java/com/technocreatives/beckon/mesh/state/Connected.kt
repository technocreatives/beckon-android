package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.extensions.getMaximumPacketSize
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.callbacks.AbstractMessageStatusCallbacks
import com.technocreatives.beckon.mesh.extensions.sequenceNumber
import com.technocreatives.beckon.mesh.model.AppKey
import com.technocreatives.beckon.mesh.model.Element
import com.technocreatives.beckon.mesh.model.Node
import com.technocreatives.beckon.mesh.model.VendorModel
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.ConfigModelAppBind
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.VendorModelMessageAcked
import timber.log.Timber

class Connected(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val beckonDevice: BeckonDevice
) : MeshState(beckonMesh, meshApi) {

    init {
        meshApi.setMeshManagerCallbacks(
            ConnectedMeshManagerCallbacks(
                beckonDevice,
                beckonMesh,
                meshApi
            )
        )
        meshApi.setMeshStatusCallbacks(
            ConnectedMessageStatusCallbacks(
                beckonDevice,
                beckonMesh,
                meshApi
            )
        )
    }

    suspend fun disconnect(): Either<Any, Loaded> = either {
        beckonDevice.disconnect().bind()
        val loaded = Loaded(beckonMesh, meshApi)
        beckonMesh.updateState(loaded)
        loaded
    }

    fun bindAppKeyToVendorModel(
        node: Node,
        appKey: AppKey,
        element: Element,
        vendorModel: VendorModel,
    ): Either<CreateMeshPduError, Unit> {
        val message = ConfigModelAppBind(element.address, vendorModel.modelId, appKey.keyIndex)
        return meshApi.createPdu(node.unicastAddress, message)
    }

    fun sendVendorModelMessageAck(
        node: Node,
        appKey: AppKey,
        vendorModel: VendorModel,
        opCode: Int,
        parameters: ByteArray
    ): Either<CreateMeshPduError, Unit> {
        val message = VendorModelMessageAcked(
            appKey.applicationKey,
            vendorModel.modelId,
            vendorModel.companyIdentifier,
            opCode,
            parameters
        )
        return meshApi.createPdu(node.unicastAddress, message)
    }

    // all other features
    init {
//        beckonDevice.connectionStates()
    }

}

class ConnectedMeshManagerCallbacks(
    private val beckonDevice: BeckonDevice,
    private val beckonMesh: BeckonMesh,
    private val meshApi: BeckonMeshManagerApi,
) : AbstractMeshManagerCallbacks() {

    override fun onMeshPduCreated(pdu: ByteArray) {
        Timber.d("sendPdu - onMeshPduCreated - ${pdu.size}")
        with(beckonMesh) {
            beckonDevice.sendPdu(
                pdu,
                com.technocreatives.beckon.mesh.MeshConstants.proxyDataInCharacteristic
            ).fold(
                { timber.log.Timber.w("SendPdu error: $it") },
                { timber.log.Timber.d("sendPdu success") }
            )
        }
    }

    override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
        Timber.d("onNetworkUpdated")
        meshApi.loadNodes()
    }

    override fun getMtu(): Int {
        return beckonDevice.getMaximumPacketSize()
    }
}

class ConnectedMessageStatusCallbacks(
    private val beckonDevice: BeckonDevice,
    private val beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
) : AbstractMessageStatusCallbacks(meshApi) {
    override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
        super.onMeshMessageReceived(src, meshMessage)
        Timber.w("onMeshMessageReceived - src: $src, dst: ${meshMessage.dst}, meshMessage: ${meshMessage.sequenceNumber()}")
    }

    override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
        Timber.w("onMeshMessageProcessed - src: $dst, dst: ${meshMessage.dst},  sequenceNumber: ${meshMessage.sequenceNumber()}")
    }

    override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
        Timber.w("onBlockAcknowledgementProcessed - dst: $dst, src: ${message.src}, sequenceNumber: ${message.sequenceNumber.littleEndianConversion()}")
    }

    override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
        Timber.w("onBlockAcknowledgementReceived - src: $src, dst: ${message.dst}, sequenceNumber: ${message.sequenceNumber.littleEndianConversion()}")
    }

    override fun onTransactionFailed(dst: Int, hasIncompleteTimerExpired: Boolean) {
        super.onTransactionFailed(dst, hasIncompleteTimerExpired)
    }

    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray?) {
        super.onUnknownPduReceived(src, accessPayload)
    }

    override fun onMessageDecryptionFailed(meshLayer: String?, errorMessage: String?) {
        super.onMessageDecryptionFailed(meshLayer, errorMessage)
    }
}