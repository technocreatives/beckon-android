package com.technocreatives.beckon.mesh.data

import arrow.core.computations.ResultEffect.bind
import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeToLongSerializer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MeshSerializationTest : StringSpec({

    val serializer = MeshConfigSerializer

    "Datetime serialization" {
        val format = Json { encodeDefaults = true }
        val date = """{"date":"2021-08-30T14:00:27Z"}"""
        val dateInLong = format.decodeFromString<DateTimeTest>(date)
        val other = format.encodeToString(date)
        println("dateInLong $dateInLong")
        println("other: $other")
//        date shouldBe other
        (dateInLong.date > 0L) shouldBe true
    }
    "mesh decode & encode" {
        MeshTestConfigs.meshConfigJsons
            .filter { it.key.contains("ios") }
            .forEach {
                println("Testing ${it.key}")
                println("Testing ${it.value}")
                val mesh = serializer.decode(it.value).bind()
                val json = serializer.encode(mesh)
                val anotherMesh = serializer.decode(json).bind()
                println(json)
                mesh shouldBe anotherMesh
            }
    }

    "Default mesh encode & decode" {
        val mesh = MeshConfigHelper.generateMesh("New Mesh", "Provisioner")
        val json = serializer.encode(mesh)
        val anotherMesh = serializer.decode(json).bind()
        mesh shouldBe anotherMesh
    }

    "networksExclusion" {
        val mesh = MeshTestConfigs.readMesh("iosWithNetworkExclusions.json")
        mesh.networkExclusions.size shouldBe 1
        mesh.networkExclusions[0].ivIndex shouldBe 0
        mesh.networkExclusions[0].addresses shouldBe listOf(UnicastAddress(1))
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

