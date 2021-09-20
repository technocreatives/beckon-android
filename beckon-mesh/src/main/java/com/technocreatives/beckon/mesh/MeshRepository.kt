package com.technocreatives.beckon.mesh

import arrow.core.Either
import java.util.*

interface MeshRepository {
    suspend fun save(mesh: MeshData): Either<Throwable, List<MeshData>>
    suspend fun remove(id: UUID): Either<Throwable, Unit>
    suspend fun currentMesh(): Either<Throwable, MeshData?>
    suspend fun find(id: UUID): Either<Throwable, MeshData?>
    suspend fun all(): Either<Throwable, List<MeshData>>
}

data class MeshData(val id: UUID, val data: String)