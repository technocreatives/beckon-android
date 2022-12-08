package com.technocreatives.beckon.mesh

import android.content.Context
import androidx.core.content.edit
import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.right
import arrow.core.rightIfNotNull
import arrow.core.zip
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.extensions.scanSingle
import com.technocreatives.beckon.mesh.data.MeshConfig
import com.technocreatives.beckon.mesh.data.MeshConfigSerializer
import com.technocreatives.beckon.mesh.data.NetworkId
import com.technocreatives.beckon.mesh.extensions.transform
import com.technocreatives.beckon.util.filterMapZ
import com.technocreatives.beckon.util.mapZ
import com.technocreatives.beckon.util.scanZ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.*

class BeckonMeshClient(
    private val context: Context,
    private val beckonClient: BeckonClient,
    private val repository: MeshRepository,
    private val config: BeckonMeshClientConfig,
) {

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
        val meshApi =
            BeckonMeshManagerApi(context, repository)
        meshApi.load(mesh.id).bind()
        BeckonMesh(context, beckonClient, meshApi, config)
    }

    suspend fun load(): Either<MeshLoadError, BeckonMesh> = either {
        disconnect().bind()
        val meshApi =
            BeckonMeshManagerApi(context, repository)
        meshApi.load().bind()
        BeckonMesh(context, beckonClient, meshApi, config)
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

    private suspend fun import(mesh: MeshData): Either<MeshLoadError, BeckonMesh> {
        val meshApi =
            BeckonMeshManagerApi(context, repository)
        return meshApi.import(mesh).map { BeckonMesh(context, beckonClient, meshApi, config) }
    }

    suspend fun fromJson(json: String) =
        withContext(Dispatchers.IO) {
            MeshConfigSerializer.decode(json)
        }

    suspend fun toJson(meshConfig: MeshConfig) =
        withContext(Dispatchers.IO) {
            MeshConfigSerializer.encode(meshConfig)
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

    suspend fun stopScan() {
        beckonClient.stopScan()
    }

    suspend fun scanSingle(): Flow<Either<ScanError, NetworkId>> =
        scanSingle(scanSetting(MeshConstants.MESH_PROXY_SERVICE_UUID))

    suspend fun scan(): Flow<Either<ScanError, List<NetworkId>>> =
        scan(scanSetting(MeshConstants.MESH_PROXY_SERVICE_UUID))

    suspend fun scanSingle(scannerSetting: ScannerSetting): Flow<Either<ScanError, NetworkId>> {
        return beckonClient.scanSingle(scannerSetting)
            .filterMapZ { it.scanRecord?.transform()?.networkId }
    }

    suspend fun scan(scannerSetting: ScannerSetting): Flow<Either<ScanError, List<NetworkId>>> =
        scanSingle(scannerSetting)
            .scanZ(emptySet<NetworkId>()) { list, id -> list + id }
            .mapZ { it.toList() }
}