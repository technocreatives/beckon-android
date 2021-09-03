package com.technocreatives.beckon.mesh.data

import kotlinx.serialization.Serializable

@Serializable
data class Element(
    val name: String,
    val index: ElementIndex,
    val location: String,
    val models: List<Model>,
)

@Serializable
@JvmInline
value class ElementIndex(val value: Int)