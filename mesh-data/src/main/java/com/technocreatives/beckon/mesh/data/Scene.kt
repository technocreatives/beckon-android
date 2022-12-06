package com.technocreatives.beckon.mesh.data

import kotlinx.serialization.Serializable

@Serializable
data class Scene(
    val name: String,
    val number: Int, // TODO create SceneNumber
    val addresses: List<UnicastAddress>
)
