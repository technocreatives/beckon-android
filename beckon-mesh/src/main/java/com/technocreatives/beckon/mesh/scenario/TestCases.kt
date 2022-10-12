package com.technocreatives.beckon.mesh.scenario

sealed interface Test {
    data class AckMessage(val message: Message, val assert: (AckMessage) -> Boolean): Test
}