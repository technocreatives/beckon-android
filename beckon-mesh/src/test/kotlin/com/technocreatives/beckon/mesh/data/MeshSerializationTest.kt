package com.technocreatives.beckon.mesh.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MeshSerializationTest : StringSpec({
    val format = Json { encodeDefaults = true }
    val jsonInput = listOf(
        "/mesh/1.json",
        "/mesh/2.json",
        "/mesh/empty.json",
        "/mesh/many.json",
        "/mesh/netkey.json",
        "/mesh/noDeviceKey.json",
    )

    "mesh decode & encode" {
        jsonInput.map {this.javaClass.stringFrom(it)}
            .forEach {
                val mesh = format.decodeFromString<Mesh>(it)
                val json = format.encodeToString(mesh)
                val anotherMesh = format.decodeFromString<Mesh>(json)
                println(mesh.meshUuid)
                mesh shouldBe anotherMesh
            }
    }

})