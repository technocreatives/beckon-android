package com.technocreatives.beckon.mesh

import java.util.*

interface MeshRepository {
    suspend fun save(mesh: MeshData): List<MeshData>
    suspend fun remove(id: UUID): List<MeshData>
    suspend fun remove(ids: List<UUID>): List<MeshData>
    suspend fun currentMesh(): MeshData?
    suspend fun find(id: UUID): MeshData?
    suspend fun all(): List<MeshData>
}

data class MeshData(val id: UUID, val data: String)