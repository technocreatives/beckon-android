package com.technocreatives.beckon.mesh.data

import android.annotation.SuppressLint
import no.nordicsemi.android.mesh.ApplicationKey
import no.nordicsemi.android.mesh.NetworkKey
import no.nordicsemi.android.mesh.models.SigModelParser
import no.nordicsemi.android.mesh.utils.AddressArray
import no.nordicsemi.android.mesh.utils.CompanyIdentifiers
import no.nordicsemi.android.mesh.utils.ProxyFilterType

@SuppressLint("RestrictedApi")
fun AppKey.transform(): ApplicationKey = ApplicationKey(index.value, key.value)

@SuppressLint("RestrictedApi")
fun NetKey.transform(): NetworkKey = NetworkKey(index.value, key.value)

fun SigModel.name(): String = SigModelParser.getSigModel(modelId.value).modelName

fun VendorModel.companyIdentifier(): Short = buffer.getShort(0)
fun VendorModel.companyName(): String = CompanyIdentifiers.getCompanyName(companyIdentifier())

internal fun FilterType.transform() = when (this) {
    FilterType.INCLUSION -> ProxyFilterType(ProxyFilterType.INCLUSION_LIST_FILTER)
    FilterType.EXCLUSION -> ProxyFilterType(ProxyFilterType.EXCLUSION_LIST_FILTER)
}

internal fun ProxyFilterType.transform() = when (this.type) {
    ProxyFilterType.INCLUSION_LIST_FILTER -> FilterType.INCLUSION
    ProxyFilterType.EXCLUSION_LIST_FILTER -> FilterType.EXCLUSION
    else -> throw IllegalArgumentException("Invalid filter type value: $type")
}

internal fun AddressArray.transform(): PublishableAddress =
    PublishableAddress.from(address)


internal fun no.nordicsemi.android.mesh.utils.ProxyFilter.transform() =
    ProxyFilter(
        filterType.transform(),
        addresses.map { it.transform() }
    )

internal fun PublishableAddress.toAddressArray(): AddressArray {
    val intAddress = value()
    val b1 = intAddress.shr(8).toByte()
    val b2 = intAddress.toByte()
    return AddressArray(b1, b2)
}
