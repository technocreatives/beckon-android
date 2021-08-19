package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.transport.Element as NrfElement

class Element(private val element: NrfElement) {
    val models get() = element.meshModels.map { it.key to MeshModel(it.value)  }
    val address get() = element.elementAddress
}