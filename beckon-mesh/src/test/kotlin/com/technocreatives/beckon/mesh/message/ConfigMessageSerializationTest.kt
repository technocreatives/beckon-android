package com.technocreatives.beckon.mesh.message

import com.technocreatives.beckon.mesh.data.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        val netKey = Mesh.randomNetKey(0, 0)
        val appKey = Mesh.randomAppKey(0, 0)
        val messages = listOf(
            GetCompositionData(1) as ConfigMessage<ConfigStatusMessage>,
            GetDefaultTtl(1) as ConfigMessage<ConfigStatusMessage>,
            SetConfigNetworkTransmit(1, 2, 2) as ConfigMessage<ConfigStatusMessage>,
            AddConfigAppKey(1, netKey, appKey) as ConfigMessage<ConfigStatusMessage>,
            DeleteConfigAppKey(1, netKey, appKey) as ConfigMessage<ConfigStatusMessage>,
            AddConfigModelSubscription(1, 2, 3, 4) as ConfigMessage<ConfigStatusMessage>,
            RemoveConfigModelSubscription(1, 2, 3, 4) as ConfigMessage<ConfigStatusMessage>,
            GetConfigModelPublication(1, 2, 3) as ConfigMessage<ConfigStatusMessage>,
            AddProxyConfigAddresses(listOf(GroupAddress(0xc000))) as ConfigMessage<ConfigStatusMessage>,
            SetConfigModelPublication(1,2,3,4,true, 1,2,3,4,5, 10) as ConfigMessage<ConfigStatusMessage>,
            SetProxyFilterType(FilterType.EXCLUSION) as ConfigMessage<ConfigStatusMessage>,
            ClearConfigModelPublication(1, 2, 3) as ConfigMessage<ConfigStatusMessage>,
            BindAppKeyToModel(1, UnicastAddress(12), ModelId(12), AppKeyIndex(1)) as ConfigMessage<ConfigStatusMessage>,
            UnbindAppKeyToModel(1, UnicastAddress(12), ModelId(12), AppKeyIndex(1)) as ConfigMessage<ConfigStatusMessage>,
            ResetNode(1) as ConfigMessage<ConfigStatusMessage>,
        )
        val json = format.encodeToString(messages)
        val other = format.decodeFromString<List<ConfigMessage<ConfigStatusMessage>>>(json)
        println(messages)
        println(json)
        println(other)
        messages shouldBe other
    }

})
