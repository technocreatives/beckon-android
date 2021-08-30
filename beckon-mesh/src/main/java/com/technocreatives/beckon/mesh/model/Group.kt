package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.Group as NrfGroup

class Group(private val group: NrfGroup) {
    val addressLabel get() = group.addressLabel
    val address get() = group.address
    val name get() = group.name
}