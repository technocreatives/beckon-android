package com.technocreatives.beckon.mesh

import android.bluetooth.BluetoothDevice
import arrow.core.Either
import arrow.core.continuations.either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.CharacteristicNotFound
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.PropertyNotSupport
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ServiceNotFound
import com.technocreatives.beckon.mesh.scenario.ConstantDelayRetry
import com.technocreatives.beckon.mesh.scenario.Retry
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.flow.first
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode

private val defaultRetry = ConstantDelayRetry(3, 1)

suspend fun BeckonMesh.scanAndConnectAfterProvisioning(
    node: ProvisionedMeshNode,
    config: ConnectionConfig,
    scanTimeout: Long = 60000,
    connectionTimeout: Long = 120000,
    retry: Retry = defaultRetry
): Either<BeckonError, BeckonDevice> = either {

    val scanResult = beckonTimeout(scanTimeout) {
        scanAfterProvisioning(node)
    }.also { stopScan() }
        .bind()

    beckonTimeout(connectionTimeout) {
        connectForProxy(scanResult.macAddress, config, retry)
    }.bind()
}

suspend fun BeckonMesh.connectForProxy(
    macAddress: MacAddress,
    config: ConnectionConfig,
    retry: Retry
) = retry({ connectForProxy(macAddress, config) }, {
    when (it) {
        is CharacteristicNotFound -> false
        is PropertyNotSupport -> false
        is ServiceNotFound -> false
        else -> true
    }
})

typealias ConnectedPredicate = (BluetoothDevice) -> Boolean

// ScanError | BeckonTimeOutError
suspend fun BeckonMesh.findProxyNode(
    timeout: Long,
    predicate: ConnectedPredicate
): Either<BeckonError, String> =
    beckonTimeout(timeout) {
        findProxyNode(predicate)
    }.also { stopScan() }

// ScanError | BeckonTimeOutError
//suspend fun BeckonMesh.findProxyNode(timeout: Long): Either<BeckonError, String> =
//    beckonTimeout(timeout) {
//        findProxyNode()
//    }.also { stopScan() }

suspend fun BeckonMesh.findProxyNode(predicate: ConnectedPredicate): Either<BeckonError, String> =
    scanForProxy(predicate)
        .mapZ { it.firstOrNull() }
        .filterZ { it != null }
        .mapZ { it!!.macAddress }
        .first()

//private suspend fun BeckonMesh.findProxyNode(): Either<ScanError, String> =
//    scanForProxy { false }
//        .mapZ { it.firstOrNull() }
//        .filterZ { it != null }
//        .mapZ { it!!.macAddress }
//        .first()