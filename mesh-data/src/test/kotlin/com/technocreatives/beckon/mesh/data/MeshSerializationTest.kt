package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeToLongSerializer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MeshSerializationTest : StringSpec({
    val format = Json { encodeDefaults = true }
    val jsonInput = listOf(
//        "/mesh/1.json",
        "/mesh/2.json",
        "/mesh/empty.json",
        "/mesh/many.json",
        "/mesh/netkey.json",
        "/mesh/noDeviceKey.json",
        "/mesh/iosConfig.json",
    )

    "Datetime serialization" {

        val date = """{"date":"2021-08-30T14:00:27Z"}"""
        val dateInLong = format.decodeFromString<DateTimeTest>(date)
        val other = format.encodeToString(date)
        println("dateInLong $dateInLong")
        println("other: $other")
//        date shouldBe other
        (dateInLong.date > 0L) shouldBe true
    }

    "mesh decode & encode" {
        jsonInput.map { this.javaClass.stringFrom(it) }
            .forEach {
                val mesh = format.decodeFromString<MeshConfig>(it)
                val json = format.encodeToString(mesh)
                val anotherMesh = format.decodeFromString<MeshConfig>(json)
                println(mesh.meshUuid)
                mesh shouldBe anotherMesh
            }
    }

    "Default mesh encode & decode" {
        val mesh = MeshConfigHelper.generateMesh("New Mesh", "Provisioner")
        val json = format.encodeToString(mesh)
        val anotherMesh = format.decodeFromString<MeshConfig>(json)
        mesh shouldBe anotherMesh
    }

//    "Default mesh with Nrf tool" {
//        val mesh = Mesh.generateMesh("nRF Mesh Network", "nRF Mesh Provisioner")
//        val json = format.encodeToString(mesh)
//        val nrfEmptyJson = this.javaClass.stringFrom("/mesh/empty.json")
//        json shouldBe nrfEmptyJson
//    }
})

@Serializable
data class DateTimeTest(
    @Serializable(with = OffsetDateTimeToLongSerializer::class)
    val date: Long
)
