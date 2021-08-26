package com.technocreatives.beckon.mesh

import android.content.Context
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.SplitPackage
import com.technocreatives.beckon.extensions.changes
import com.technocreatives.beckon.extensions.writeSplit
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.extensions.sequenceNumber
import com.technocreatives.beckon.mesh.model.AppKey
import com.technocreatives.beckon.mesh.model.Group
import com.technocreatives.beckon.mesh.model.Node
import com.technocreatives.beckon.mesh.model.toNode
import com.technocreatives.beckon.mesh.utils.tap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber
import java.util.*
import kotlin.coroutines.CoroutineContext
import com.technocreatives.beckon.mesh.model.NetworkKey as BeckonNetworkKey

class BeckonMeshManagerApi(
    context: Context,
) : MeshManagerApi(context), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job

    private val nodesSubject = MutableStateFlow<List<Node>>(emptyList())
    fun nodes(): Flow<List<Node>> = nodesSubject.asStateFlow()

    fun loadNodes(): List<Node> {
        val appKeys = appKeys()
        val netKeys = networkKeys()
        return meshNetwork().nodes.map { it.toNode(appKeys, netKeys) }
    }

    fun appKeys(): List<AppKey> =
        meshNetwork().appKeys.map { AppKey(it) }

    fun appKey(index: Int): AppKey? =
        appKeys().find { it.keyIndex == index }

    fun networkKeys(): List<BeckonNetworkKey> =
        meshNetwork().netKeys.map { BeckonNetworkKey(it) }

    fun groups(): List<Group> =
        meshNetwork().groups.map { Group(it) }

    suspend fun updateNodes() {
        nodesSubject.emit(loadNodes())
    }

    internal fun meshNetwork(): MeshNetwork = meshNetwork!!

    fun createPdu(dst: Int, meshMessage: MeshMessage): Either<SendMessageError, Unit> {
        Timber.w("createMeshPdu dst: $dst, sequenceNumber: ${meshMessage.sequenceNumber()}")
        return try {
            createMeshPdu(dst, meshMessage).right()
        } catch (ex: IllegalArgumentException) {
            ex.createMeshPduError(dst).left()
        }
    }

    suspend fun load(id: UUID): Either<MeshLoadError, Unit> =
        withContext(Dispatchers.IO) {
            val networkLoadingEmitter =
                CompletableDeferred<Either<NetworkLoadFailedError, Unit>>()
            setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
                override fun onNetworkLoadFailed(error: String?) {
                    networkLoadingEmitter.complete(
                        NetworkLoadFailedError(
                            id,
                            "MeshNetwork is empty"
                        ).left()
                    )
                }
                override fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
                    networkLoadingEmitter.complete(Unit.right())
                }
            })
            loadMeshNetwork()
            networkLoadingEmitter.await().tap { updateNodes() }
        }

    suspend fun load(): Either<MeshLoadError, Unit> =
        withContext(Dispatchers.IO) {
            val networkLoadingEmitter =
                CompletableDeferred<Either<CreateNetworkFailedError, Unit>>()
            setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
                override fun onNetworkLoadFailed(error: String?) {
                    networkLoadingEmitter.complete(
                        CreateNetworkFailedError(
                            "MeshNetwork is empty"
                        ).left()
                    )
                }
                override fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
                    networkLoadingEmitter.complete(Unit.right())
                }
            })
            loadMeshNetwork()
            networkLoadingEmitter.await().tap { updateNodes() }
        }

    suspend fun import(mesh: Mesh): Either<MeshLoadError, Unit> =
        withContext(Dispatchers.IO) {
            val networkLoadingEmitter =
                CompletableDeferred<Either<NetworkImportedFailedError, Unit>>()
            setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
                override fun onNetworkImportFailed(error: String?) {
                    networkLoadingEmitter.complete(
                        NetworkImportedFailedError(
                            mesh.id,
                            "Cannot import mesh ${mesh.id}"
                        ).left()
                    )
                }

                override fun onNetworkImported(meshNetwork: MeshNetwork?) {
                    networkLoadingEmitter.complete(Unit.right())
                }
            })

            importMeshNetworkJson(mesh.data)
            networkLoadingEmitter.await().tap { updateNodes() }
        }

    suspend fun exportCurrentMesh(id: UUID): Mesh? =
        withContext(Dispatchers.IO) {
            exportMeshNetwork()?.let { Mesh(id, it) }
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

private fun IllegalArgumentException.createMeshPduError(dst: Int) =
    when (message) {
        "Invalid address, destination address must be a valid 16-bit value." -> InvalidAddress(
            dst
        )
        "Label UUID unavailable for the virtual address provided" -> LabelUuidUnavailable
        else -> ProvisionerAddressNotSet
    }