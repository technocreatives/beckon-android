package com.technocreatives.beckon.mesh.model

import android.os.Parcelable
import com.technocreatives.beckon.MacAddress
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class UnprovisionedScanResult(
    val macAddress: MacAddress,
    val name: String?,
    val rssi: Int,
    val uuid: UUID
) : Parcelable