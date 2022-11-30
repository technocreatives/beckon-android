package com.technocreatives.beckon.mesh.data

import arrow.core.Either
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object MeshConfigSerializer {

    private val default = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true

    }

    private val prettyFormat = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun decode(string: String): Either<Throwable, MeshConfig> = Either.catch {
        default.decodeFromString(string)
    }

    fun encode(meshConfig: MeshConfig) = default.encodeToString(meshConfig)

    fun toJsonPretty(meshConfig: MeshConfig): String = prettyFormat.encodeToString(meshConfig)
}
