package com.technocreatives.beckon.mesh

import arrow.core.Either
import arrow.core.right
import com.technocreatives.beckon.BeckonClient
import no.nordicsemi.android.mesh.MeshNetwork
import java.lang.Exception

sealed interface MeshError

data class IllegalMeshStateError(val state: MState) : MeshError, Exception()

data class MeshLoadFailedError(val error: String) : MeshError