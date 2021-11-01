package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Scene(
    @Serializable(with = UuidSerializer::class)
    val meshUuid: UUID
)
