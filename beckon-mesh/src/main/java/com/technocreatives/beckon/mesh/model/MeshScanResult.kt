package com.technocreatives.beckon.mesh.model

import com.technocreatives.beckon.mesh.data.Key
import no.nordicsemi.android.support.v18.scanner.ScanRecord

// todo scanRecord => mac address
data class MeshScanResult(val scanRecord: ScanRecord, val networkId: NetworkId?)

@JvmInline
value class NetworkId(val value: ByteArray)