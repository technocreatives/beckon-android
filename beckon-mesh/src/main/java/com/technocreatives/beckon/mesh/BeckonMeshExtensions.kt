package com.technocreatives.beckon.mesh

import android.bluetooth.BluetoothDevice
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.right
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.mesh.scenario.ConstantDelayRetry
import com.technocreatives.beckon.mesh.scenario.Retry
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapEither
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.flow.first
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import timber.log.Timber

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
    }.tapLeft { stopScan() }
        .bind()

    beckonTimeout(connectionTimeout) {
        retry {
            connectForProxy(scanResult.macAddress, config)
        }
    }.bind()
}

typealias ConnectedPredicate = (BluetoothDevice) -> Boolean

// ScanError | BeckonTimeOutError
suspend fun BeckonMesh.findProxyNode(
    timeout: Long,
    predicate: ConnectedPredicate
): Either<BeckonError, String> =
    beckonTimeout(timeout) {
        findProxyNode(predicate)
    }

// ScanError | BeckonTimeOutError
suspend fun BeckonMesh.findProxyNode(timeout: Long): Either<BeckonError, String> =
    beckonTimeout(timeout) {
        findProxyNode()
    }

suspend fun BeckonMesh.findProxyNode(predicate: ConnectedPredicate): Either<BeckonError, String> =
    scanForProxy(predicate)
        .mapZ { it.firstOrNull() }
        .filterZ { it != null }
        .mapZ { it!! }
        .mapEither {
            Timber.d("Scenario execute try to connect to $it")
            stopScan()
            it.macAddress.right()
        }
        .first()

private suspend fun BeckonMesh.findProxyNode(): Either<ScanError, String> =
    scanForProxy { false }
        .mapZ { it.firstOrNull() }
        .filterZ { it != null }
        .mapZ { it!! }
        .mapEither {
            Timber.d("Scenario execute try to connect to $it")
            stopScan()
            it.macAddress.right()
        }
        .first()