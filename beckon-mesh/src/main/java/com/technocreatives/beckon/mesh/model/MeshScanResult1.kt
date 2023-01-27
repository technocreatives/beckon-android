package com.technocreatives.beckon.mesh.model

import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.mesh.data.NetworkId
import com.technocreatives.beckon.mesh.extensions.ProxyAdvertisedData
import no.nordicsemi.android.support.v18.scanner.ScanRecord

// todo scanRecord => mac address
data class MeshScanResult1(val scanRecord: ScanRecord, val networkId: NetworkId?)
data class ProxyScanResult(val address: MacAddress, val name: String?, val data: ProxyAdvertisedData)
