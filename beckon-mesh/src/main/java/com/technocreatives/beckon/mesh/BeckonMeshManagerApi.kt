package com.technocreatives.beckon.mesh

import android.content.Context
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.identity
import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.extensions.changes
import com.technocreatives.beckon.extensions.writeSplit
import com.technocreatives.beckon.mesh.model.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.MeshNetwork
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class BeckonMeshManagerApi(
    context: Context,
) : MeshManagerApi(context), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job

    private val nodesSubject = MutableSharedFlow<List<Node>>()
    fun nodes(): Flow<List<Node>> = nodesSubject.asSharedFlow()

    fun loadNodes(): List<Node> {
        // todo background thread
        return meshNetwork().nodes.map { Node(it) }
    }

    suspend fun updateNodes() {
        nodesSubject.emit(loadNodes())
    }

    private fun meshNetwork(): MeshNetwork = meshNetwork!!

    suspend fun BeckonDevice.sendPdu(
        pdu: ByteArray,
        characteristic: Characteristic
    ): Either<BeckonActionError, Unit> =
        either {
            // todo order of things
            val splitPackage = writeSplit(pdu, characteristic).bind()
            Timber.d("onDataSend: ${splitPackage.data.value?.size}")
            coroutineScope {
                launch {
                    handleWriteCallbacks(splitPackage.mtu, splitPackage.data.value!!)
                }
            }
        }

    suspend fun BeckonDevice.handleNotifications(characteristic: Characteristic) {
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

