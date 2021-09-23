package com.technocreatives.beckon.mesh

import android.content.Context
import androidx.core.content.edit
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.right
import arrow.core.rightIfNotNull
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.mesh.data.Mesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class BeckonMeshClient(
    private val context: Context,
    private val beckonClient: BeckonClient,
    private val repository: MeshRepository
) {
//    private val meshApi by lazy {
//        BeckonMeshManagerApi(context, repository)
//    }

    private val meshApi =
        BeckonMeshManagerApi(context, repository)
    private var currentMesh: BeckonMesh? = null

    private suspend fun disconnect(): Either<MeshLoadError, Unit> {
        val result = currentMesh?.let {
            it.disconnect().mapLeft { BleDisconnectError(it.throwable) }.map { }
        } ?: Unit.right()
        currentMesh?.unregister()
        currentMesh = null
        return result
    }

    suspend fun loadCurrentMesh(): Either<MeshLoadError, BeckonMesh> = either {
        disconnect().bind()
        val meshOrNull = repository.currentMesh()
            .mapLeft { DatabaseError(it) }
            .bind()
        val mesh = meshOrNull.rightIfNotNull { NoCurrentMeshFound }.bind()
        meshApi.load(mesh.id).bind()
        BeckonMesh(context, beckonClient, meshApi)
    }

    suspend fun load(): Either<MeshLoadError, BeckonMesh> = either {
        disconnect().bind()
        meshApi.load().bind()
        BeckonMesh(context, beckonClient, meshApi)
    }

    suspend fun import(id: UUID): Either<MeshLoadError, BeckonMesh> = either {
        disconnect().bind()
        val mesh = findMeshById(id).bind()
        import(mesh).bind()
    }

    suspend fun findMeshById(id: UUID): Either<MeshLoadError, MeshData> = either {
        disconnect().bind()
        val meshOrNull = repository.find(id)
            .mapLeft { DatabaseError(it) }
            .bind()
        meshOrNull.rightIfNotNull { MeshIdNotFound(id) }.bind()
    }

    /**
     * load current mesh or import mesh data
     * */
    suspend fun loadOrImport(id: UUID): Either<MeshLoadError, BeckonMesh> = either {
        if (id == currentMeshID()) {
            load().bind()
        } else {
            val mesh = findMeshById(id).bind()
            import(mesh).bind().also {
                setCurrentMeshId(id)
            }
        }
    }

    private suspend fun import(mesh: MeshData): Either<MeshLoadError, BeckonMesh> =
        meshApi.import(mesh).map { BeckonMesh(context, beckonClient, meshApi) }

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

    private suspend fun currentMeshID(): UUID? =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString("mesh_uuid", null)?.let {
                UUID.fromString(it)
            }
        }

    private fun setCurrentMeshId(id: UUID) =
        sharedPreferences.edit(commit = true) {
            this.putString("mesh_uuid", id.toString())
        }
}