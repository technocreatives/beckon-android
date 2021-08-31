package com.technocreatives.beckon.mesh.state


import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.BeckonMeshManagerApi


sealed class MeshState(val beckonMesh: BeckonMesh, val meshApi: BeckonMeshManagerApi) {
    abstract suspend fun isValid(): Boolean
}

