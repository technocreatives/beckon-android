package com.technocreatives.beckon.mesh.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

object MeshTestConfigs {
    private const val meshBasePath = "/mesh/"

    val meshConfigJsons: List<String> by lazy {
        val meshFolder = File(javaClass.getResource(meshBasePath).file)
        meshFolder.listFiles()!!
            .toList()
            .map(File::readText)
    }
    val meshConfigs by lazy {
        meshConfigJsons
            .map { it.asMeshConfig() }
    }

    private val format = Json { encodeDefaults = true }

    private fun String.asMeshConfig(): MeshConfig = format.decodeFromString(this)

    fun readMesh(meshName: String): MeshConfig {
        val meshFile = File(javaClass.getResource(meshBasePath + meshName).file)
        return meshFile.readText().asMeshConfig()
    }

}
