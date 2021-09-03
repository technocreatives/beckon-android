package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.HexToIntSerializer
import kotlinx.serialization.Serializable

@Serializable
data class Element(
    @Transient val address: UnicastAddress = UnicastAddress(0),
    val name: String = "", // TODO ios is missing
    val index: ElementIndex,
    @Serializable(with = HexToIntSerializer::class)
    val location: Int,
    val models: List<Model>,
)

@Serializable
@JvmInline
value class ElementIndex(val value: Int)