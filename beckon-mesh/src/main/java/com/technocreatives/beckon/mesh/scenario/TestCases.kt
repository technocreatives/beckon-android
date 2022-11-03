package com.technocreatives.beckon.mesh.scenario

import arrow.core.*
import arrow.core.continuations.either
import arrow.fx.coroutines.parZip
import arrow.typeclasses.Semigroup
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.data.ProxyFilterMessage
import com.technocreatives.beckon.mesh.data.PublishableAddress
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface Test {

    data class VendorMessageAck(
        val address: PublishableAddress,
        val message: SendVendorModelMessage,
        val responseOpCode: Int,
        val assert: Assertion<VendorModelMessageStatus>
    ) : Test

    data class VendorMessage(
        val address: PublishableAddress,
        val message: SendVendorModelMessage,
        val expected: Set<ProxyFilterMessage>
    ) : Test

    object Empty : Test

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
): Either<Nel<E>, List<B>> =
    Semigroup.nonEmptyList<E>().run {
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
    suspend fun run(case: TestCase) =
        either {
            Timber.d("Setup")
            with(case.before) { beckonMesh.execute() }.bind()
            Timber.d("Running test")
            val result = case.tests.mapAccumulating { runTest(it) }.bind()
            Timber.d("After")
            with(case.after) { beckonMesh.execute() }.bind()
        }

    private suspend fun runTest(test: Test): Either<Any, Unit> = either {
        Timber.d("Running test $test")
        val connected = beckonMesh.connectedState().bind()
        when (test) {
            is Test.VendorMessageAck -> connected.runTest(test).bind()
            is Test.VendorMessage -> connected.runTest(test).bind()
            Test.Empty -> Unit.right()
        }
    }

    private suspend fun Connected.runTest(test: Test.VendorMessageAck): Either<TestFailed, Unit> =
        either {
            val respond =
                sendVendorModelMessageAck(test.address, test.message, test.responseOpCode)
                    .mapLeft { TestFailed.ExecutionError(it) }
                    .bind()
            test.assert.assert(respond).bind()
        }

    private suspend fun Connected.runTest(test: Test.VendorMessage): Either<TestFailed, Unit> =
        either {

            val result = parZip(
                {
                    beckonMesh.proxyFilterMessages().take(test.expected.size)
                        .collectUntil(30.seconds)
                },
                {
                    sendVendorModelMessage(test.address, test.message)
                        .mapLeft { TestFailed.ExecutionError(it) }
                        .bind()
                }
            ) { r, _ -> r }

            Timber.d("ProxyFilterMessages $result")

            ensure(result.toSet() == test.expected) {
                Timber.w("Failed $result")
                TestFailed.NotEqual(
                    test,
                    test.expected,
                    result.toSet()
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
    data class ExecutionError(val error: Any) : TestFailed
    data class NotEqual<T>(val test: Test, val expected: T, val actual: T) : TestFailed
}

sealed interface AssertionFailed : TestFailed