package com.technocreatives.beckon.mesh.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeSameSizeAs
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi

@ExperimentalSerializationApi
class NodesTest : StringSpec({

    "Assert nodes without provisioners + provisioners are same as all nodes" {
        MeshTestConfigs.meshConfigs.forEach { meshConfig ->
            val nodesWithoutProvisioners = meshConfig.nodesWithoutProvisioners().toSet()
            val provisionersNodes = meshConfig.provisionerNodes().toSet()
            val allNodes = meshConfig.nodes.toSet()

            nodesWithoutProvisioners union provisionersNodes shouldBe allNodes
            nodesWithoutProvisioners intersect provisionersNodes shouldHaveSize 0

        }
    }

    "Assert provisioners are found as nodes" {
        MeshTestConfigs.meshConfigs.forEach {
            it.provisioners shouldBeSameSizeAs it.provisionerNodes()
        }
    }

    "Assert provisioners have same uuid as in nodes" {
        MeshTestConfigs.meshConfigs.forEach {
            it.provisioners.map { it.id }.toSet() shouldBe it.provisionerNodes()
                .map { it.id }.toSet()
        }
    }
})

