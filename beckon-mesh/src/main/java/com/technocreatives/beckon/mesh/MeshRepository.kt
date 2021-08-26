package com.technocreatives.beckon.mesh

import java.util.*

interface MeshRepository {
    suspend fun save(mesh: Mesh): List<Mesh>
    suspend fun remove(id: UUID): List<Mesh>
    suspend fun remove(ids: List<UUID>): List<Mesh>
    suspend fun currentMesh(): Mesh?
    suspend fun find(id: UUID): Mesh?
    suspend fun all(): List<Mesh>
}

data class Mesh(val id: UUID, val data: String)