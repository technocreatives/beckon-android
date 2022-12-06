package com.technocreatives.beckon.mesh.data

import arrow.core.computations.ResultEffect.bind
import java.io.File

object MeshTestConfigs {
    private const val meshBasePath = "/mesh/"

    val meshConfigJsons: Map<String, String> by lazy {
        val meshFolder = File(javaClass.getResource(meshBasePath).file)
        meshFolder.listFiles()!!
            .toList().associate { it.name to it.readText() }
    }
    val meshConfigs by lazy {
        meshConfigJsons
            .map { it.value.asMeshConfig() }
    }

    private fun String.asMeshConfig(): MeshConfig = MeshConfigSerializer.decode(this).bind()

    fun readMesh(meshName: String): MeshConfig {
        val meshFile = File(javaClass.getResource(meshBasePath + meshName).file)
        return meshFile.readText().asMeshConfig()
    }

}
