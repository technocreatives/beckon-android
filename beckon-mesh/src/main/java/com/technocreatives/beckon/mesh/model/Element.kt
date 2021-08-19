package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.models.VendorModel as NrfVendorModel
import no.nordicsemi.android.mesh.transport.Element as NrfElement
import no.nordicsemi.android.mesh.transport.MeshModel as NrfMeshModel

class Element(private val element: NrfElement) {
    val models get() = element.meshModels.map { it.key to it.value.to() }
    val address get() = element.elementAddress
}

private fun NrfMeshModel.to(): MeshModel =
    if (this is NrfVendorModel) {
        VendorModel(this)
    } else {
        UnknownModel(this)
    }