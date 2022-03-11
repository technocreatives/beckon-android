package com.technocreatives.beckon.mesh

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.mesh.scenario.ConstantDelayRetry
import com.technocreatives.beckon.mesh.scenario.Retry
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
    }.tapLeft { stopScan() }
        .bind()

    beckonTimeout(connectionTimeout) {
        retry {
            connectForProxy(scanResult.macAddress, config)
        }
    }.bind()
}