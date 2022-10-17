package com.technocreatives.beckon.mesh.scenario

import android.bluetooth.BluetoothAdapter
import arrow.core.*
import arrow.core.continuations.either
import arrow.fx.coroutines.raceN
import com.technocreatives.beckon.*
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.ConnectionConfig
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.FilterType
import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.message.ConfigMessage
import com.technocreatives.beckon.mesh.message.setAddressesToProxy
import com.technocreatives.beckon.mesh.model.UnprovisionedScanResult
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapEither
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber

private val TyriLightConnectionConfig = ConnectionConfig(Mtu(69))
private const val TIME_OUT_FOR_STEP: Long = 360000
//private const val TIME_OUT_FOR_STEP: Long = 60

sealed interface Step1 {

    object AutoConnect : Step1
    data class Connect(val address: MacAddress) : Step1
    data class ConnectAfterProvisioning(val address: Int) : Step1
    object Disconnect : Step1

    data class CreateGroup(val address: GroupAddress) : Step1
    data class Provision(val address: MacAddress) : Step1
    data class Message(val message: ConfigMessage<*>) : Step1
    data class UnAckMessage(val message: ConfigMessage<*>) : Step1

    object OnOffBluetooth : Step1
    data class Delay(val time: Long) : Step1
}

class Processor(val beckonMesh: BeckonMesh) {
    fun process(step1: Step1): Either<Any, Unit> = TODO()
}

sealed interface Step {
    suspend fun BeckonMesh.execute(): Either<Any, Unit>
    suspend fun BeckonMesh.execute1(): Either<StepError, StepResult> =
        StepError.ConnectStepError.left()
}

data class Provision(val address: MacAddress) : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        Timber.d("Execute Provision $address started")

        val scanResult = scanForProvisioning(address, TIME_OUT_FOR_STEP).bind()

        Timber.d("Execute Provision $address scanResult: $scanResult")
        stopScan()
        val retry = ExponentialBackOffRetry(5, 360)

        val beckonDevice = retry {
            connectForProvisioning(scanResult, TIME_OUT_FOR_STEP, TyriLightConnectionConfig)
        }.bind()

        Timber.d("Execute Provision connected ${beckonDevice.metadata().macAddress}")

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
        val beckonDevice = scanForProxy()
            .mapZ { it.firstOrNull() }
            .filterZ { it != null }
            .mapZ { it!! }
            .mapEither {
                Timber.d("Scenario execute try to connect to $it")
                stopScan()
                val retry = ExponentialBackOffRetry(5, 360)
                retry {
                    connectForProxy(it.macAddress, TyriLightConnectionConfig)
                }
            }
            .first().bind()
        startConnectedState(beckonDevice).bind()
    }
}

object SetProxyFilter : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> = either {
        val connected = connectedState().bind()
        connected.setAddressesToProxy(FilterType.INCLUSION)
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

object OnOffBluetooth : Step {
    override suspend fun BeckonMesh.execute(): Either<Any, Unit> {
        Timber.d("Execute OnOffBluetooth")
        onOffBluetooth(60000)
        return Unit.right()
    }
}

private suspend fun onOffBluetooth(delayInMs: Long) {
    Timber.d("Execute OnOffBluetooth daylay: $delayInMs")
    val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    mBluetoothAdapter.disable()
    delay(delayInMs)
    mBluetoothAdapter.enable()
    delay(delayInMs)
}

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
        val beckonDevice = scanForProxy()
            .mapZ { it.firstOrNull { it.macAddress == address } }
//            .mapZ { it.firstOrNull() }
            .filterZ { it != null }
            .mapZ { it!! }
            .mapEither { scanResult ->
                Timber.d("Scenario execute try to connect to $scanResult")
                stopScan()
                val retry = ExponentialBackOffRetry(5, 360)
                val result = retry {
                    connectForProxy(scanResult.macAddress, TyriLightConnectionConfig)
                }
                result.fold({
                    if (it is ConnectionError) {
                        Timber.d("Connection error, so we turn on/off BLT and try again")
                        onOffBluetooth(10000)
                        retry {
                            connectForProxy(scanResult.macAddress, TyriLightConnectionConfig)
                        }
                    } else {
                        result
                    }
                }, { result })
            }
            .first().bind()
        startConnectedState(beckonDevice).bind()
    }
}

data class Process(
    val steps: List<Step>,
    val retry: Retry = InstantRetry(3),
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
        val results = processes.traverse { with(it) { execute() } }.bind()
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
    ).mapLeft {
        stopScan()
        Timber.e("scanForProvisioning timeout $macAddress")
        BeckonTimeOutError
    }.flatMap(::identity)


suspend fun BeckonMesh.connectForProvisioning(
    scanResult: UnprovisionedScanResult,
    timeout: Long,
    config: ConnectionConfig
): Either<BeckonError, BeckonDevice> =
    raceN(
        { delay(timeout) },
        {
            connectForProvisioning(scanResult, config)
        }
    ).mapLeft {
        // disconnect
        Timber.e("connectForProvisioning timeout ${scanResult.macAddress}")
        BeckonTimeOutError
    }.flatMap(::identity)


/***
 * scan and connect to a device by it's unicast address
 * with retry and timeout
 */
internal suspend fun BeckonMesh.connectForProxy(
    address: Int,
    timeout: Long
): Either<Any, BeckonDevice> =
    raceN(
        { delay(timeout) },
        {
            scanForNodeIdentity(address)
                .mapZ { it.firstOrNull() }
                .filterZ { it != null }
                .mapZ { it!! }
                .mapEither { scanResult ->
                    // try to connect
                    Timber.d("Scenario execute try to connect to $scanResult")
                    stopScan()
                    val retry = ExponentialBackOffRetry(5, 360) // 5 retries in max 6 minutes
                    val result = retry {
                        connectForProxy(scanResult.macAddress, ConnectionConfig(null))
                    }
                    result.fold({
                        if (it is ConnectionError) {
                            Timber.d("Connection error, so we turn on/off BLT and try again")
                            onOffBluetooth(60000)
                            retry {
                                connectForProxy(scanResult.macAddress, TyriLightConnectionConfig)
                            }
                        } else {
                            result
                        }
                    }, { result })
                }
                .first()
        })
        .mapLeft {
            stopScan()
            Timber.e("connectForProxy timeout $address")
            BeckonTimeOutError
        }
        .flatMap(::identity)