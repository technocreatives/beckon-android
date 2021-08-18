package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.models.VendorModel as NrfVendorModel

class VendorModel(private val model: NrfVendorModel) {
    val modelId get() = model.modelId
    val companyIdentifier get() = model.companyIdentifier
}