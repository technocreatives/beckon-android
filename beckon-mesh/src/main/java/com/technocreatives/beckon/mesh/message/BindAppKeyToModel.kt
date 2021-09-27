package com.technocreatives.beckon.mesh.message

import com.technocreatives.beckon.mesh.data.AppKeyIndex
import com.technocreatives.beckon.mesh.data.ModelId
import com.technocreatives.beckon.mesh.data.UnicastAddress
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.transport.ConfigModelAppBind
import no.nordicsemi.android.mesh.transport.ConfigModelAppUnbind

data class BindAppKeyToModel(
    override val dst: Int,
    val elementAddress: UnicastAddress,
    val modelId: ModelId,
    val appKeyIndex: AppKeyIndex,
) : ConfigMessage {
    override val responseOpCode = StatusOpCode.ConfigModelSubscription

    override fun toMeshMessage() = ConfigModelAppBind(
        elementAddress.value,
        modelId.value,
        appKeyIndex.value
    )

    operator fun not() = UnbindAppKeyToModel(dst, elementAddress, modelId, appKeyIndex)
}

data class UnbindAppKeyToModel(
    override val dst: Int,
    val elementAddress: UnicastAddress,
    val modelId: ModelId,
    val appKeyIndex: AppKeyIndex,
) : ConfigMessage {
    override val responseOpCode = StatusOpCode.ConfigModelSubscription

    override fun toMeshMessage() = ConfigModelAppUnbind(
        elementAddress.value,
        modelId.value,
        appKeyIndex.value
    )

    operator fun not() = BindAppKeyToModel(dst, elementAddress, modelId, appKeyIndex)
}