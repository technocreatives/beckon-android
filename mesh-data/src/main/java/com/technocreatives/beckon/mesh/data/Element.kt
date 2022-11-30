package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.HexToIntSerializer
import kotlinx.serialization.Serializable

@Serializable
data class Element(
    val name: String?,
    val index: ElementIndex,
    @Serializable(with = HexToIntSerializer::class)
    val location: Int, // TODO ElementLocationAddress 4 char hex
    val models: List<Model>,
)

// TODO must be value of 0-255
@Serializable
@JvmInline
value class ElementIndex(val value: Int)