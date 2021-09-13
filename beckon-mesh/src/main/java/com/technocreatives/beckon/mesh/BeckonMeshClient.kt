package com.technocreatives.beckon.mesh

import android.content.Context
import androidx.core.content.edit
import arrow.core.*
import arrow.core.computations.either
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.mesh.data.Mesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

class BeckonMeshClient(
    private val context: Context,
    private val beckonClient: BeckonClient,
    private val repository: MeshRepository
) {
    private val meshApi by lazy {
        BeckonMeshManagerApi(context, repository)
    }

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
        BeckonMesh(context, beckonClient, meshApi, repository)
    }

    suspend fun load(): Either<MeshLoadError, BeckonMesh> = either {
        disconnect().bind()
        meshApi.load().bind()
        BeckonMesh(context, beckonClient, meshApi, repository)
    }

    suspend fun import(id: UUID): Either<MeshLoadError, BeckonMesh> = either {
        disconnect().bind()
        val mesh = repository.find(id).rightIfNotNull { MeshIdNotFound(id) }.bind()
        import(mesh).bind()
    }

    /**
     * load current mesh or import mesh data
     * */
    suspend fun loadOrImport(id: UUID): Either<MeshLoadError, BeckonMesh> = either {
        if (id == currentMeshID()) {
            load().bind()
        } else {
            val mesh = repository.find(id).rightIfNotNull { MeshIdNotFound(id) }.bind()
            import(mesh).bind().also {
                setCurrentMeshId(id)
            }
        }
    }

    private suspend fun import(mesh: MeshData): Either<MeshLoadError, BeckonMesh> =
        meshApi.import(mesh).map { BeckonMesh(context, beckonClient, meshApi, repository) }

    fun generateMesh(meshName: String, provisionerName: String): Mesh =
        Mesh.generateMesh(meshName, provisionerName)

    suspend fun fromJson(json: String) =
        withContext(Dispatchers.IO) {
            Mesh.fromJson(json)
        }

    suspend fun toJson(mesh: Mesh) =
        withContext(Dispatchers.IO) {
            Mesh.toJson(mesh)
        }

    private val sharedPreferences by lazy {
        context.getSharedPreferences(
            "com.technocreatives.beckon.mesh",
            Context.MODE_PRIVATE
        )
    }

    private fun currentMeshID(): UUID? =
        sharedPreferences.getString("mesh_uuid", null)?.let {
            UUID.fromString(it)
        }

    private fun setCurrentMeshId(id: UUID) =
        sharedPreferences.edit(commit = true) {
            this.putString("mesh_uuid", id.toString())
        }
}

}