package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.transport.MeshModel as NrfMeshModel
import no.nordicsemi.android.mesh.models.VendorModel as NrfVendorModel

sealed class MeshModel(open val model: NrfMeshModel, val index: Int, val appKeys: List<AppKey>) {
    val modelId get() = model.modelId
    val name get() = model.modelName
}

class VendorModel(override val model: NrfVendorModel, index: Int, appKeys: List<AppKey>) :
    MeshModel(model, index, appKeys) {
    val companyIdentifier get() = model.companyIdentifier
}

class UnknownModel(model: NrfMeshModel, index: Int, appKeys: List<AppKey>) :
    MeshModel(model, index, appKeys)
