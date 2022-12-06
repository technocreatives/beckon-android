package com.technocreatives.beckon.mesh.scenario

import arrow.core.*
import arrow.core.continuations.either
import arrow.fx.coroutines.parZip
import arrow.typeclasses.Semigroup
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.data.ProxyFilterMessage
import com.technocreatives.beckon.mesh.data.PublishableAddress
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.message.SendVendorModelMessage
import com.technocreatives.beckon.mesh.message.sendVendorModelMessage
import com.technocreatives.beckon.mesh.message.sendVendorModelMessageAck
import com.technocreatives.beckon.mesh.state.Connected
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.mesh.transport.VendorModelMessageStatus
import timber.log.Timber
import kotlin.experimental.ExperimentalTypeInference
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface Test {

    data class VendorMessageAck(
        val address: PublishableAddress,
        val message: SendVendorModelMessage,
        val responseOpCode: Int,
        val assert: Assertion<VendorModelMessageStatus>
    ) : Test

    // for reliability / success-rate
    data class SuccessRate(
        val address: GroupAddress,
        val message: SendVendorModelMessage,
        val expected: ExpectedSuccessRate,
    ) : Test

    data class SingleVendorMessage(
        val address: UnicastAddress,
        val message: SendVendorModelMessage,
        val assert: Assertion<ProxyFilterMessage>,
    ) : Test

    data class AssertOnly(
        val expectedSize: Int,
        val assert: Assertion<List<ProxyFilterMessage>>
    ) : Test

    data class MultipleVendorMessage(
        val vendorMessages: List<Test>
    ) : Test

    data class Action(val execute: suspend () -> Either<TestFailed, Unit>) : Test

    data class Repeat(val times: Int, val test: Test) : Test

    object Empty : Test
}

data class ExpectedSuccessRate(val total: Int, val expectedPercentage: Float, val timeout: Duration = 30.seconds) {
    fun expectedSize(): Int = (total * expectedPercentage).roundToInt()
}

interface Assertion<T> {
    fun assert(t: T): Either<AssertionFailed, Unit>
}

data class TestCase(val before: Scenario, val after: Scenario, val tests: List<Test>)

@PublishedApi
internal fun <T> Iterable<T>.collectionSizeOrDefault(default: Int): Int =
    if (this is Collection<*>) this.size else default

@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
public inline fun <E, A, B> Iterable<A>.mapAccumulating(
    f: (A) -> Either<E, B>
): Either<Nel<E>, List<B>> = Semigroup.nonEmptyList<E>().run {
    fold(Either.Right(ArrayList<B>(collectionSizeOrDefault(10))) as Either<Nel<E>, MutableList<B>>) { acc, a ->
        when (val res = f(a)) {
            is Either.Right -> when (acc) {
                is Either.Right -> acc.also { it.value.add(res.value) }
                is Either.Left -> acc
            }
            is Either.Left -> when (acc) {
                is Either.Right -> nonEmptyListOf(res.value).left()
                is Either.Left -> acc.value.combine(nonEmptyListOf(res.value)).left()
            }
        }
    }
}

class TestRunner(val beckonMesh: BeckonMesh) {
    suspend fun run(case: TestCase) = either {
        Timber.d("Setup")
        with(case.before) { beckonMesh.execute() }.bind()
        Timber.d("Running test")
        case.tests.mapAccumulating { runTest(it) }.bind()
        Timber.d("After")
        with(case.after) { beckonMesh.execute() }.bind()
    }

    private suspend fun runTest(test: Test): Either<TestFailed, Unit> = either {
        Timber.d("Running test $test")
        val connected =
            beckonMesh.connectedState().mapLeft { TestFailed.NotInConnectedSTate }.bind()
        when (test) {
            is Test.VendorMessageAck -> connected.runTest(test).bind()
            is Test.SuccessRate -> connected.runTest(test).bind()
            is Test.SingleVendorMessage -> connected.runTest(test).bind()
            is Test.MultipleVendorMessage -> connected.runTest(test).bind()
            Test.Empty -> Unit.right()
            is Test.Action -> test.execute()
            is Test.Repeat -> List(test.times) { runTest(test.test) }.mapAccumulating { it }
                .bimap({ TestFailed.MultipleFailed(it) }) {}.bind()
            is Test.AssertOnly -> connected.runTest(test).bind()
        }
    }

    private suspend fun Connected.runTest(test: Test.MultipleVendorMessage): Either<TestFailed, Unit> =
        test.vendorMessages.mapAccumulating { runTest(it) }
            .bimap({ TestFailed.MultipleFailed(it) }) {}

    private suspend fun Connected.runTest(test: Test.SingleVendorMessage): Either<TestFailed, Unit> =
        either {
            val result: ProxyFilterMessage = parZip({
                beckonMesh.proxyFilterMessages().take(1).collectUntil(30.seconds)
            }, {
                sendVendorModelMessage(
                    test.address,
                    test.message
                ).mapLeft { TestFailed.ExecutionError(it) }.bind()
            }) { r, _ -> r[0] }

            test.assert.assert(result).bind()

        }

    private suspend fun Connected.runTest(test: Test.AssertOnly): Either<TestFailed, Unit> =
        either {
            val result: List<ProxyFilterMessage> = beckonMesh.proxyFilterMessages().take(test.expectedSize).collectUntil(30.seconds)

            test.assert.assert(result).bind()
        }

    private suspend fun Connected.runTest(test: Test.VendorMessageAck): Either<TestFailed, Unit> =
        either {
            val respond = sendVendorModelMessageAck(
                test.address,
                test.message,
                test.responseOpCode
            ).mapLeft { TestFailed.ExecutionError(it) }.bind()
            test.assert.assert(respond).bind()
        }

    private suspend fun Connected.runTest(test: Test.SuccessRate): Either<TestFailed, Unit> =
        either {

            val result: List<ProxyFilterMessage> = parZip({
                beckonMesh.proxyFilterMessages().take(test.expected.total).collectUntil(test.expected.timeout)
            }, {
                sendVendorModelMessage(
                    test.address,
                    test.message
                ).mapLeft { TestFailed.ExecutionError(it) }.bind()
            }) { r, _ -> r }

            Timber.d("ProxyFilterMessages $result")

            ensure(result.size >= test.expected.expectedSize()) {
                Timber.w("Failed $result")
                TestFailed.NotEqual(
                    test, result
                )
            }
        }
}

suspend fun <A> Flow<A>.collectUntil(duration: Duration): List<A> {
    val result = mutableListOf<A>()
    withTimeoutOrNull(duration) { collect { result.add(it) } }
    return result.toList()
}

sealed interface TestFailed {
    data class ExecutionError(val error: Any?) : TestFailed
    data class NotEqual<T>(val test: Test, val actual: T) : TestFailed
    data class MultipleFailed(val fails: Nel<TestFailed>) : TestFailed
    object NotInConnectedSTate : TestFailed
}

sealed interface MqttConnectError : TestFailed {
    object TokenNullError: MqttConnectError
    data class ConnectFailed(val throwable: Throwable?): MqttConnectError
    data class DisconnectFailed(val throwable: Throwable?): MqttConnectError
}

sealed interface AssertionFailed : TestFailed {
    data class NotEqual<T>(val expected: T, val actual: T) : AssertionFailed
    data class MultipleFailed(val fails: Nel<TestFailed>) : AssertionFailed
}

