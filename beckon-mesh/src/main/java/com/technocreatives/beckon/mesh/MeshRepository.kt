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

object NoopRepository : MeshRepository {
    override suspend fun save(mesh: Mesh): List<Mesh> {
        return emptyList()
    }

    override suspend fun remove(id: UUID): List<Mesh> {
        return emptyList()
    }

    override suspend fun remove(ids: List<UUID>): List<Mesh> {
        return emptyList()
    }

    override suspend fun find(id: UUID): Mesh? {
        return null
    }

    override suspend fun currentMesh(): Mesh? {
        return Mesh(UUID.randomUUID(), "")
    }


    override suspend fun all(): List<Mesh> {
        return emptyList()
    }

}

data class Mesh(val id: UUID, val data: String)