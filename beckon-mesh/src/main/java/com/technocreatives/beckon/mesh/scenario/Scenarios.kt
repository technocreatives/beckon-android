package com.technocreatives.beckon.mesh.scenario

import arrow.core.prependTo
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.mesh.data.AppKey
import com.technocreatives.beckon.mesh.data.NetKey
import com.technocreatives.beckon.mesh.message.ResetNode

private fun createTestCase(
    addresses: List<MacAddress>,
    netKey: NetKey,
    appKey: AppKey,
    shouldUnprovision: Boolean,
): Scenario {

    val lastAddress = addresses[addresses.size - 1]
    val provisionCases = addresses.mapIndexed { index, address ->
        Process.provisionCase(address, index * 1 + 2, netKey, appKey)
    }

    val processes = if (shouldUnprovision) {
        val unprovisionAll =
            Connect(lastAddress).prependTo(addresses.mapIndexed { index, _ ->
                Message(ResetNode(index + 2))
            })
        provisionCases.plus(Process(unprovisionAll))
    } else
        provisionCases

    return Scenario(processes)
}