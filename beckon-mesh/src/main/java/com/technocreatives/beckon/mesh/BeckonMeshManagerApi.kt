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
import com.technocreatives.beckon.mesh.data.Mesh
import com.technocreatives.beckon.mesh.data.NetKey
import com.technocreatives.beckon.mesh.data.Node
import com.technocreatives.beckon.mesh.data.transform
import com.technocreatives.beckon.mesh.extensions.info
import com.technocreatives.beckon.mesh.extensions.sequenceNumber
import com.technocreatives.beckon.mesh.utils.tap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber
import java.util.*
import kotlin.coroutines.CoroutineContext
import com.technocreatives.beckon.mesh.model.NetworkKey as BeckonNetKey

class BeckonMeshManagerApi(
    context: Context,
) : MeshManagerApi(context), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job

    //    private val nodesSubject = MutableStateFlow<List<Node>>(emptyList())
    private val meshSubject by lazy {
        MutableStateFlow(meshNetwork().transform())
    }

    //    fun nodes(): StateFlow<List<Node>> = nodesSubject.asStateFlow()
    fun meshes(): StateFlow<Mesh> = meshSubject.asStateFlow()

//    private suspend fun loadNodes(): List<Node> =
//        withContext(Dispatchers.IO) {
//            val appKeys = appKeys()
//            val netKeys = networkKeys()
//            meshNetwork().nodes.map { it.toNode(appKeys, netKeys) }
//        }

    suspend fun nodes(key: NetKey): List<Node> =
        withContext(Dispatchers.IO) {
            val mesh = meshNetwork().transform()
            mesh.nodes.filter { it.netKeys.any { it.index == key.index } }
        }

//    fun networkKeys(): List<BeckonNetKey> =
//        meshNetwork().netKeys.map { BeckonNetKey(it) }
//
    suspend fun updateNetwork() {
        meshSubject.emit(meshNetwork().transform())
//        nodesSubject.emit(loadNodes())
//        groupsSubject.emit(meshNetwork().groups.map { Group(it) })
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
            networkLoadingEmitter.await().tap { updateNetwork() }
        }

    suspend fun load(): Either<MeshLoadError, Unit> =
        withContext(Dispatchers.IO) {
            val networkLoadingEmitter =
                CompletableDeferred<Either<CreateNetworkFailedError, Unit>>()
            setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
                override fun onNetworkLoadFailed(error: String?) {
                    networkLoadingEmitter.complete(
                        CreateNetworkFailedError(
                            error ?: "NetworkLoadFailed is empty"
                        ).left()
                    )
                }

                override fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
                    networkLoadingEmitter.complete(Unit.right())
                    Timber.d("===== onNetworkUpdated")
                    meshNetwork!!.netKeys.map {
                        Timber.d("==== Updated NetKey ${it.info()}")
                    }
                }
            })
            loadMeshNetwork()
            networkLoadingEmitter.await().tap { updateNetwork() }
        }

    suspend fun import(mesh: MeshData): Either<MeshLoadError, Unit> =
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
            networkLoadingEmitter.await().tap { updateNetwork() }
        }

    suspend fun exportCurrentMesh(id: UUID): MeshData? =
        withContext(Dispatchers.IO) {
            exportMeshNetwork()?.let { MeshData(id, it) }
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

    private fun handleWriteCallbacks(splitPackage: SplitPackage) =
        launch {
            handleWriteCallbacks(splitPackage.mtu, splitPackage.data.value!!)
        }

    fun BeckonDevice.handleNotifications(characteristic: Characteristic) =
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