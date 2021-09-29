package com.technocreatives.beckon.mesh.scenario

import arrow.core.prependTo
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.message.*


object Processes {

    fun unProvisionAll(addresses: List<MacAddress>): Process {

        val lastAddress = addresses[addresses.size - 1]
        return Process(
            Connect(lastAddress)
                .prependTo(addresses.mapIndexed { index, _ ->
                    Message(ResetNode(index + 2))
                })
        )
    }

    fun provisionDevice(
        macAddress: MacAddress,
        nodeAddress: UnicastAddress,
        netKey: NetKey,
        appKey: AppKey
    ): Process =
        Process(
            listOf(
//                Delay(1000),
                Provision(macAddress),
//                Delay(1000),
                ConnectAfterProvisioning(nodeAddress.value),
                Message(GetCompositionData(nodeAddress.value)),
                Message(GetDefaultTtl(nodeAddress.value)),
                Message(SetConfigNetworkTransmit(nodeAddress.value, 2, 1)),
                Message(AddConfigAppKey(nodeAddress.value, netKey, appKey)),
                Message(
                    BindAppKeyToModel(
                        nodeAddress.value,
                        nodeAddress,
                        ModelId(134615040),
                        appKey.index
                    )
                ),
                Disconnect,
            )
        )

    fun bindAppKeyToModels() {

    }
    fun bindAppKeyToModels(
        addresses: List<UnicastAddress>,
        modelId: ModelId,
        appKeyIndex: AppKeyIndex
    ) =
        Process(
            addresses.map { Message(BindAppKeyToModel(it.value, it, modelId, appKeyIndex)) }
        )

    fun createGroupAndByModelsToIt(
        groupName: String,
        groupAddress: GroupAddress,
        addresses: List<UnicastAddress>,
        modelId: ModelId
    ): Process {
        val createGroup = CreateGroup(groupAddress, groupName)
        return Process(createGroup.prependTo(addresses.map {
            Message(
                AddConfigModelSubscription(
                    it.value,
                    it.value,
                    groupAddress.value,
                    modelId.value
                )
            )
        }))
    }
}