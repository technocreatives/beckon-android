package com.technocreatives.beckon.mesh.scenario

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.right
import arrow.core.traverseEither
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.message.ConfigMessage
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

data class Message(val message: ConfigMessage<*>) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val connected = connectedState().bind()
        val response = connected.bearer.sendConfigMessage(message).bind()
        response
    }

}

data class CreateGroup(val groupAddress: GroupAddress, val name: String) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        createGroup(name, groupAddress.value).bind()
    }
}

object Disconnect : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        disconnect().bind()
    }
}

data class Delay(val time: Long) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> {
        delay(time)
        return Unit.right()
    }
}

// connect to what ever in the mesh
object AutoConnect : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val beckonDevice = scanForProxy { false }
            .mapZ { it.firstOrNull() }
            .filterZ { it != null }
            .mapZ { it!! }
            .mapEither {
                Timber.d("Scenario execute try to connect to $it")
                stopScan()
                val retry = RepeatRetry(3)
                retry {
                    connectForProxy(it.macAddress)
                }
            }
            .first().bind()
        startConnectedState(beckonDevice).bind()
    }
}

data class ConnectAfterProvisioning(val address: Int) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val beckonDevice = scanForProxy(address)
//            .mapZ { it.firstOrNull { it.macAddress == address } }
            .mapZ { it.firstOrNull() }
            .filterZ { it != null }
            .mapZ { it!! }
            .mapEither {
                Timber.d("Scenario execute try to connect to $it")
                stopScan()
                val retry = RepeatRetry(3)
                retry {
                    connectForProxy(it.macAddress)
                }
            }
            .first().bind()
        startConnectedState(beckonDevice).bind()
    }
}

data class Connect(val address: MacAddress) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val beckonDevice = scanForProxy { false }
            .mapZ { it.firstOrNull { it.macAddress == address } }
//            .mapZ { it.firstOrNull() }
            .filterZ { it != null }
            .mapZ { it!! }
            .mapEither {
                Timber.d("Scenario execute try to connect to $it")
                stopScan()
                val retry = RepeatRetry(3)
                retry {
                    connectForProxy(it.macAddress)
                }
            }
            .first().bind()
        startConnectedState(beckonDevice).bind()
    }
}

data class Process(
    val steps: List<Step>,
    val retry: Retry = RepeatRetry(3),
) {

    suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        Timber.d("Process start with ${steps.size} steps")
        val start = System.nanoTime()
        val results = steps.traverseStep(retry) { with(it) { execute() } }.bind()
        val end = System.nanoTime()
        Timber.d("Process end with result=$results, in ${(end - start) / 1_000_000_000} seconds")
    }
}

data class Scenario(val processes: List<Process>) {
    suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        Timber.d("Scenario execute start with ${processes.size} processes")
        val start = System.nanoTime()
        disconnect().bind()
        val results = processes.traverseEither { with(it) { execute() } }.bind()
        val end = System.nanoTime()
        Timber.d("Scenario execute end with result=$results, in ${(end - start) / 1_000_000_000} seconds")
    }
}

suspend inline fun <E, A, B> List<A>.traverseStep(
    retry: Retry,
    crossinline f: suspend (A) -> Either<E, B>,
): Either<E, List<B>> {
    val destination = ArrayList<B>(size)
    forEachIndexed { index, item ->
        Timber.d("Execute Step ${index + 1} - $item")
        when (val res = retry { f(item) }) {
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


