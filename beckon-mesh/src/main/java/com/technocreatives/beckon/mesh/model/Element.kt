package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.models.VendorModel as NrfVendorModel
import no.nordicsemi.android.mesh.transport.Element as NrfElement
import no.nordicsemi.android.mesh.transport.MeshModel as NrfMeshModel

class Element(
    private val element: NrfElement, val index: Int, private val appKeys: List<AppKey>
) {
    val models get() = element.meshModels.map { it.value.to(it.key) }
    val address get() = element.elementAddress
}

private fun NrfMeshModel.to(index: Int, appKeys: List<AppKey>): MeshModel {
     val keys = boundAppKeyIndexes.mapNotNull { key -> appKeys.find { it.keyIndex == key }

    return if (this is NrfVendorModel) {
        VendorModel(this, index, keys)
    } else {
        UnknownModel(this, index, keys)
    }
     }