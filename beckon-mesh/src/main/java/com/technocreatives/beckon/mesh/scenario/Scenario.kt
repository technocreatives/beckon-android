package com.technocreatives.beckon.mesh.scenario

import arrow.core.*
import arrow.core.computations.either
import arrow.fx.coroutines.raceN
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.BeckonTimeOutError
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.MeshConstants
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.message.ConfigMessage
import com.technocreatives.beckon.mesh.model.UnprovisionedScanResult
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapEither
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber

private const val TIME_OUT_FOR_STEP: Long = 60000

sealed interface Step {
    suspend fun BeckonMesh.execute(): Either<Any, Unit>
    suspend fun BeckonMesh.execute1(): Either<StepError, StepResult> =
        StepError.ConnectStepError.left()
}

data class Provision(val address: MacAddress) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val scanResult = scanForProvisioning(address, TIME_OUT_FOR_STEP).bind()
        stopScan()
        val beckonDevice = connectForProvisioning(scanResult, TIME_OUT_FOR_STEP).bind()
        val provisioning = startProvisioning(beckonDevice).bind()
        val unProvisionedNode = provisioning.identify(scanResult, 5).bind()
        val provisionedNode = provisioning.startProvisioning(unProvisionedNode).bind()
        provisionedNode
    }

}

data class Message(val message: ConfigMessage<*>) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val connected = connectedState().bind()
        connected.bearer.sendConfigMessage(message).bind()
    }
}

data class UnAckMessage(val message: ConfigMessage<*>) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val connected = connectedState().bind()
        connected.bearer.sendConfigMessageUnAck(message).bind()
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
        Timber.d("Delay $time")
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

        val beckonDevice = connectForProxy(address, TIME_OUT_FOR_STEP).bind()
        startConnectedState(beckonDevice).bind()
    }
}

class MessageAndOnErrorAction(val message: ConfigMessage<*>, val action: () -> Unit)

//val messages: List<MessageAndOnErrorAction> = TODO()
//fun onError(message: StepError.MessageStepError) {
//
//   messages.firstOrNull {it.message == message.message}?.let {
//       it.action()
//   }
//}

sealed interface StepError {
    data class MessageStepError(val error: SendAckMessageError, val message: ConfigMessage<*>) :
        StepError

    object ConnectStepError : StepError
}

sealed interface StepResult {
    object MessageStepError : StepResult
    object ConnectStepError : StepResult
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

suspend fun da(beckonMesh: BeckonMesh) {
    with(Process(emptyList())) {
        beckonMesh.execute1()
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

    suspend fun BeckonMesh.execute1(): Either<StepError, List<StepResult>> = either {
        Timber.d("Process start with ${steps.size} steps")
        val start = System.nanoTime()
        val results = steps.traverseStep(retry) { with(it) { execute1() } }.bind()
        val end = System.nanoTime()
        Timber.d("Process end with result=$results, in ${(end - start) / 1_000_000_000} seconds")
        results
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

private suspend inline fun <E, A, B> List<A>.traverseStep(
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


internal suspend fun BeckonMesh.scanForProvisioning(macAddress: MacAddress, timeout: Long):
        Either<BeckonError, UnprovisionedScanResult> =
    raceN(
        { delay(timeout) },
        {
            scanForProvisioning()
                .mapZ { it.firstOrNull { it.macAddress == macAddress } }
                .filterZ { it != null }
                .mapZ { it!! }
                .first()
        }
    ).mapLeft { BeckonTimeOutError }.flatMap(::identity)


internal suspend fun BeckonMesh.connectForProvisioning(
    macAddress: MacAddress,
    timeout: Long = 60000
): Either<BeckonError, BeckonDevice> {
    return raceN(
        { delay(timeout) },
        {
            scanForProvisioning()
                .mapZ { it.firstOrNull { it.macAddress == macAddress } }
                .filterZ { it != null }
                .mapEither { connectForProvisioning(it!!) }
                .first()
        }
    ).mapLeft { BeckonTimeOutError }.flatMap(::identity)

}

suspend fun BeckonMesh.connectForProvisioning(scanResult: UnprovisionedScanResult, timeout: Long): Either<BeckonError, BeckonDevice> =
    raceN(
        { delay(timeout) },
        {
            connectForProvisioning(scanResult)
        }
    ).mapLeft { BeckonTimeOutError }.flatMap(::identity)


internal suspend fun BeckonMesh.connectForProxy(
    address: Int,
    timeout: Long
): Either<Any, BeckonDevice> =
    raceN(
        { delay(timeout) },
        {
            scanForProxy(address)
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
                .first()
        }).flatMap(::identity)