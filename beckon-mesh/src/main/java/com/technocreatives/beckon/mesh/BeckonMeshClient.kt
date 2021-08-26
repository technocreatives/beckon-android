package com.technocreatives.beckon.mesh

import android.content.Context
import arrow.core.*
import arrow.core.computations.either
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        meshApi.load(mesh.id).bind()
        BeckonMesh(context, beckonClient, meshApi)
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

    private suspend fun import(mesh: Mesh): Either<MeshLoadError, BeckonMesh> =
        meshApi.import(mesh).map { BeckonMesh(context, beckonClient, meshApi) }

}