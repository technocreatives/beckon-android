package com.technocreatives.beckon.mesh

import android.content.Context
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import kotlinx.coroutines.CompletableDeferred
import no.nordicsemi.android.mesh.MeshNetwork
import java.util.*

class BeckonMeshClient(
    private val context: Context,
    private val beckonClient: BeckonClient,
    private val repository: MeshRepository = NoopRepository
) {
    private val meshApi = BeckonMeshManagerApi(context)

    private var currentMesh: BeckonMesh? = null

    private suspend fun disconnect(): Either<MeshLoadError, Unit> {
        val result = currentMesh?.let {
            it.disconnect().mapLeft { BleDisconnectError(it.throwable) }.map { }
        } ?: Unit.right()
        currentMesh = null
        return result
    }

    suspend fun loadCurrentMesh(): Either<MeshLoadError, BeckonMesh> = either {
        disconnect().bind()
        val mesh = repository.currentMesh().rightIfNotNull { NoCurrentMeshFound }.bind()
        loadFromDatabase(mesh.id).bind()
    }

    suspend fun import(id: UUID): Either<MeshLoadError, BeckonMesh> = either {
        disconnect().bind()
        val mesh = repository.find(id).rightIfNotNull { MeshIdNotFound(id) }.bind()
        import(mesh).bind()
    }

    /**
     * load current mesh or
     * */
    suspend fun loadOrImport(id: UUID): Either<MeshLoadError, BeckonMesh> = either {
        val currentMesh = repository.currentMesh()
        if (currentMesh != null && currentMesh.id == id) {
            loadCurrentMesh().bind()
        } else {
            val mesh = repository.find(id).rightIfNotNull { MeshIdNotFound(id) }.bind()
            import(mesh).bind()
        }
    }

    private suspend fun loadFromDatabase(id: UUID): Either<MeshLoadError, BeckonMesh> = either {
        val networkLoadingEmitter =
            CompletableDeferred<Either<NetworkLoadFailedError, Unit>>()
        meshApi.setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
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
        meshApi.loadMeshNetwork()
        networkLoadingEmitter.await().bind()
        meshApi.updateNodes()
        BeckonMesh(context, beckonClient, meshApi)
    }

    private suspend fun import(mesh: Mesh): Either<MeshLoadError, BeckonMesh> = either {
        val networkLoadingEmitter =
            CompletableDeferred<Either<NetworkImportedFailedError, Unit>>()
        meshApi.setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
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

        meshApi.importMeshNetworkJson(mesh.data)
        networkLoadingEmitter.await().bind()
        meshApi.updateNodes()
        BeckonMesh(context, beckonClient, meshApi)
    }
}