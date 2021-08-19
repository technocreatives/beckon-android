package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.transport.MeshModel as NrfMeshModel
import no.nordicsemi.android.mesh.models.VendorModel as NrfVendorModel

sealed class MeshModel(open val model: NrfMeshModel) {
    val modelId get() = model.modelId
}

class VendorModel(override val model: NrfVendorModel): MeshModel(model) {
    val companyIdentifier get() = model.companyIdentifier
}

class UnknownModel(model: NrfMeshModel): MeshModel(model)