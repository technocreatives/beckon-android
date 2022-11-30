package com.technocreatives.beckon.mesh.data

import arrow.core.toOption
import arrow.optics.Optional
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.*

class MeshConfigTest : StringSpec({
    val meshId = UUID.fromString("bee85314-83e8-459b-b47a-faa6b555f286")
    val provisionerId = UUID.fromString("ecd34225-f269-42e0-aee3-097f17c558d0")
    val mesh = MeshConfigHelper.generateMesh("mesh", "provisioner")
    "provisioner unicast" {
        val provisionerId: Optional<MeshConfig, NodeId> =
            MeshConfig.provisioners compose Optional.listHead() compose Provisioner.id

        val uuid = provisionerId.getOrNull(mesh)!!

//        val nodeUnicast = MeshConfig.nodes compose Index.list() compose Node.unicastAddress
//        FilterIndex.list<Node>().filter()

        val nodeIdOptional = listPredicate<Node> { it.id == uuid }

        val provisionerUnicastOptional =
            MeshConfig.nodes compose nodeIdOptional compose Node.unicastAddress

        val newMesh = provisionerUnicastOptional.modify(mesh) { UnicastAddress(100) }

        newMesh.nodes[0].unicastAddress shouldBe UnicastAddress(100)
    }


})

fun <A> listPredicate(p: (A) -> Boolean): Optional<List<A>, A> = Optional(
    getOption = { it.firstOrNull(p).toOption() },
    set = { list, newHead ->
        val index = list.indexOfFirst(p)

        if (index != -1) list.take(index) + newHead + list.drop(index + 1) else list
    }
)