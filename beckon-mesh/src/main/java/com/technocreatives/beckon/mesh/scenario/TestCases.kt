package com.technocreatives.beckon.mesh.scenario

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.right
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.data.PublishableAddress
import com.technocreatives.beckon.mesh.message.SendVendorModelMessage
import com.technocreatives.beckon.mesh.message.sendVendorModelMessage
import com.technocreatives.beckon.mesh.message.sendVendorModelMessageAck
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.VendorModelMessageStatus
import timber.log.Timber

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
    ) : Test


    object Empty : Test

}

interface Assertion<T> {
    fun assert(t: T): Either<AssertionFailed, Unit>
}

data class TestCase(val before: Scenario, val after: Scenario, val test: Test)

class TestRunner(val beckonMesh: BeckonMesh) {
    suspend fun run(case: TestCase) =
        either {
            Timber.d("Setup")
            with(case.before) { beckonMesh.execute() }.bind()
            Timber.d("Running test")
            runTest(case.test).bind()
            Timber.d("After")
            with(case.after) { beckonMesh.execute() }.bind()
        }

    private suspend fun runTest(test: Test): Either<Any, Unit> = either {
        Timber.d("Running test $test")
        val connected = beckonMesh.connectedState().bind()
        when (test) {
            is Test.VendorMessageAck -> connected.runTest(test)
            is Test.VendorMessage -> TODO()
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
            val respond =
                sendVendorModelMessage(test.address, test.message)
                    .mapLeft { TestFailed.ExecutionError(it) }
                    .bind()
            beckonMesh.states()
        }

}

sealed interface TestFailed {
    data class ExecutionError(val error: Any) : TestFailed
}

sealed interface AssertionFailed : TestFailed