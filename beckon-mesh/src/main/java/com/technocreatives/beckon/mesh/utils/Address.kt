package com.technocreatives.beckon.mesh.utils

import no.nordicsemi.android.mesh.utils.MeshAddress as NrfMeshAddress

object MeshAddress {
    fun isValidGroupAddress(address: ByteArray) = NrfMeshAddress.isValidGroupAddress(address)
    fun isValidGroupAddress(address: Int) = NrfMeshAddress.isValidGroupAddress(address)
    fun isValidVirtualAddress(address: Int) = NrfMeshAddress.isValidGroupAddress(address)
    fun isValidVirtualAddress(address: ByteArray) = NrfMeshAddress.isValidGroupAddress(address)
    fun isValidUnicastAddress(address: Int) = NrfMeshAddress.isValidGroupAddress(address)
    fun isValidUnicastAddress(address: ByteArray) = NrfMeshAddress.isValidGroupAddress(address)
}