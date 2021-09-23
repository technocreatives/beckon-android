package com.technocreatives.beckon.mesh.scenario

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.right
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.data.AppKey
import com.technocreatives.beckon.mesh.data.NetKey
import com.technocreatives.beckon.mesh.message.*
import com.technocreatives.beckon.mesh.model.UnprovisionedScanResult
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapEither
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber

sealed interface Step {
    suspend fun BeckonMesh.execute(): Either<Any, Unit>
}

data class Provision(val address: MacAddress) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val scanResult = scanForProvisioning(address).bind()
        stopScan()
        val beckonDevice = connectForProvisioning(scanResult).bind()
        val provisioning = startProvisioning(beckonDevice).bind()
        val unProvisionedNode = provisioning.identify(scanResult, 5).bind()
        val provisionedNode = provisioning.startProvisioning(unProvisionedNode).bind()
        provisionedNode
    }
}

data class Message(val message: AckBeckonMessage) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val connected = connectedState().bind()
        val response = connected.bearer.sendBeckonMessage(message).bind()
        response
    }

}

object Disconnect : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        disconnect().bind()
    }
}

// connect to what ever
data class Connect(val address: MacAddress) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        delay(1000)
        val beckonDevice = scanForProxy { it.address == address }
            .mapZ { it.firstOrNull { it.macAddress == address } }
            .filterZ { it != null }
            .mapZ { it!! }
            .mapEither { connectForProxy(it.macAddress) }
            .first().bind()
        stopScan()
        startConnectedState(beckonDevice).bind()
    }
}

data class Scenario(val steps: List<Step>) {
    suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        Timber.d("Scenario execute start: ${steps.size}")
        disconnect().bind()
        val results = steps.traverseStep { with(it) { execute() } }
        Timber.d("Scenario execute end: $results")
        disconnect().bind()
        Unit
    }


    companion object {
        fun simpleCase(macAddress: MacAddress, nodeAddress: Int, netKey: NetKey, appKey: AppKey) =
            Scenario(
                listOf(
                    Provision(macAddress),
                    Connect(macAddress),
                    Message(GetCompositionData(nodeAddress)),
                    Message(GetDefaultTtl(nodeAddress)),
                    Message(SetConfigNetworkTransmit(nodeAddress, 2, 1)),
                    Message(AddConfigAppKey(nodeAddress, netKey, appKey)),
                    Disconnect,
                )
            )
    }
}

inline fun <E, A, B> List<A>.traverseStep(f: (A) -> Either<E, B>): Either<E, List<B>> {
    val destination = ArrayList<B>(size)
    forEachIndexed { index, item ->
        Timber.d("Execute Step ${index + 1} - $item")
        when (val res = f(item)) {
            is Either.Right -> destination.add(res.value)
            is Either.Left -> return res
        }
    }
    return destination.right()
}


internal suspend fun BeckonMesh.scanForProvisioning(macAddress: MacAddress): Either<BeckonError, UnprovisionedScanResult> =
    scanForProvisioning()
        .mapZ { it.firstOrNull { it.macAddress == macAddress } }
        .filterZ { it != null }
        .mapZ { it!! }
        .first()

internal suspend fun BeckonMesh.connectForProvisioning(macAddress: MacAddress): Either<BeckonError, BeckonDevice> =
    scanForProvisioning()
        .mapZ { it.firstOrNull { it.macAddress == macAddress } }
        .filterZ { it != null }
        .mapEither { connectForProvisioning(it!!) }
        .first()


