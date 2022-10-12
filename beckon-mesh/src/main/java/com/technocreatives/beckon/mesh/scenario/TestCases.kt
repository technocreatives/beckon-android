package com.technocreatives.beckon.mesh.scenario

import arrow.core.Either
import arrow.core.continuations.either
import com.technocreatives.beckon.mesh.BeckonMesh

sealed interface Test {
    data class AckMessage(val message: Message, val assert: (AckMessage) -> Boolean) : Test
}

data class TestCase(val before: Scenario, val test: Test, val after: Scenario)

class TestRunner(val beckonMesh: BeckonMesh) {
    suspend fun run(case: TestCase) =
        either {
            with(case.before) { beckonMesh.execute() }.bind()
            runTest(case.test).bind()
            with(case.after) { beckonMesh.execute() }.bind()
        }

    private suspend fun runTest(test: Test): Either<Any, Unit> = TODO()
}