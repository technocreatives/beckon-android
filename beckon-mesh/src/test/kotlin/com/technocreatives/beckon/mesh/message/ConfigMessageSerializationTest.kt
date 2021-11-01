package com.technocreatives.beckon.mesh.message

import com.technocreatives.beckon.mesh.data.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@ExperimentalSerializationApi
private val format by lazy {
    Json {
        encodeDefaults = true; explicitNulls = false; prettyPrint = true
    }
}

class ConfigMessageSerializationTest : StringSpec({

    "one message decode & encode" {
        val compositionData: ConfigMessage<GetCompositionDataResponse> = GetCompositionData(1)
        val json = format.encodeToString(compositionData)
        val other = format.decodeFromString<ConfigMessage<GetCompositionDataResponse>>(json)
        println(compositionData)
        println(json)
        println(other)
        compositionData shouldBe other
    }

    "list of messages decode & encode" {
        val netKey = MeshConfigHelper.randomNetKey(0, 0)
        val appKey = MeshConfigHelper.randomAppKey(0, 0)
        val messages = listOf(
            GetCompositionData(1),
            GetDefaultTtl(1),
            SetConfigNetworkTransmit(1, 2, 2),
            AddConfigAppKey(1, netKey, appKey),
            DeleteConfigAppKey(1, netKey, appKey),
            AddConfigModelSubscription(1, 2, 3, 4),
            RemoveConfigModelSubscription(1, 2, 3, 4),
            GetConfigModelPublication(1, UnicastAddress(2), ModelId(3)),
            AddProxyConfigAddresses(listOf(GroupAddress(0xc000))),
            SetConfigModelPublication(
                1, UnicastAddress(2), Publish(
                    GroupAddress(1),
                    AppKeyIndex(1),
                    Period(1, 1),
                    false,
                    127,
                    Retransmit(1, 1)


                ), ModelId(10)
            ),
            SetProxyFilterType(FilterType.EXCLUSION),
            ClearConfigModelPublication(1, UnicastAddress(2), ModelId(3)),
            BindAppKeyToModel(1, UnicastAddress(12), ModelId(12), AppKeyIndex(1)),
            UnbindAppKeyToModel(1, UnicastAddress(12), ModelId(12), AppKeyIndex(1)),
            ResetNode(1),
        )
        val json = format.encodeToString(messages)
        val other = format.decodeFromString<List<ConfigMessage<ConfigStatusMessage>>>(json)
        println(messages)
        println(json)
        println(other)
        messages shouldBe other
    }

})
