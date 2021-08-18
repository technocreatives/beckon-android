package com.technocreatives.beckon.mesh

import android.content.Context
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.identity
import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.SplitPackage
import com.technocreatives.beckon.extensions.changes
import com.technocreatives.beckon.extensions.writeSplit
import com.technocreatives.beckon.mesh.model.Node
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class BeckonMeshManagerApi(
    context: Context,
) : MeshManagerApi(context), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job

    private val nodesSubject = MutableSharedFlow<List<Node>>()
    fun nodes(): Flow<List<Node>> = nodesSubject.asSharedFlow()

    fun loadNodes(): List<Node> {
        // todo background thread maybe?
        return meshNetwork().nodes.map { Node(it) }
    }

    suspend fun updateNodes() {
        nodesSubject.emit(loadNodes())
    }

    private fun meshNetwork(): MeshNetwork = meshNetwork!!


    override fun createMeshPdu(dst: Int, meshMessage: MeshMessage) {
        Timber.w("createMeshPdu dst: $dst, meshMessage: ${meshMessage.javaClass}: ${meshMessage.message}")
        super.createMeshPdu(dst, meshMessage)
    }

    suspend fun BeckonDevice.sendPdu(
        pdu: ByteArray,
        characteristic: Characteristic
    ): Either<BeckonActionError, Unit> =
        either {
            Timber.w("sendPdu pdu size: ${pdu.size}")
            val splitPackage = writeSplit(pdu, characteristic).bind()
            Timber.d("onDataSend: ${splitPackage.data.value?.size}")
            handleWriteCallbacks(splitPackage)
        }

    private fun handleWriteCallbacks(splitPackage: SplitPackage) {
        launch {
            handleWriteCallbacks(splitPackage.mtu, splitPackage.data.value!!)
        }
    }

    fun BeckonDevice.handleNotifications(characteristic: Characteristic) {
        launch {
            changes(characteristic.uuid, ::identity)
                .onEach {
                    Timber.w("Device changes $it")
                }
                .collect {
                    Timber.d("OnDataReceived: ${metadata().macAddress} ${it.data.value?.size}")
                    handleNotifications(
                        mtu().maximumPacketSize(),
                        it.data.value!!
                    )
                }
        }
    }

    fun close() {
        job.cancel()
    }
}
