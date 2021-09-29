package com.technocreatives.beckon.mesh.message

import com.technocreatives.beckon.mesh.data.AppKeyIndex
import com.technocreatives.beckon.mesh.data.ModelId
import com.technocreatives.beckon.mesh.data.UnicastAddress
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.transport.ConfigModelAppBind
import no.nordicsemi.android.mesh.transport.ConfigModelAppStatus
import no.nordicsemi.android.mesh.transport.ConfigModelAppUnbind
import no.nordicsemi.android.mesh.transport.MeshMessage

@Serializable
@SerialName("BindAppKeyToModel")
data class BindAppKeyToModel(
    override val dst: Int,
    val elementAddress: UnicastAddress,
    val modelId: ModelId,
    val appKeyIndex: AppKeyIndex,
) : ConfigMessage<ConfigMessageStatus>() {
    override val responseOpCode = StatusOpCode.ConfigModelApp

    override fun toMeshMessage() = ConfigModelAppBind(
        elementAddress.value,
        modelId.value,
        appKeyIndex.value
    )

    override fun fromResponse(message: MeshMessage): ConfigMessageStatus =
        (message as ConfigModelAppStatus).transform()

    operator fun not() = UnbindAppKeyToModel(dst, elementAddress, modelId, appKeyIndex)
}

@Serializable
@SerialName("UnbindAppKeyToModel")
data class UnbindAppKeyToModel(
    override val dst: Int,
    val elementAddress: UnicastAddress,
    val modelId: ModelId,
    val appKeyIndex: AppKeyIndex,
) : ConfigMessage<ConfigMessageStatus>() {
    override val responseOpCode = StatusOpCode.ConfigModelApp

    override fun toMeshMessage() = ConfigModelAppUnbind(
        elementAddress.value,
        modelId.value,
        appKeyIndex.value
    )

    override fun fromResponse(message: MeshMessage): ConfigMessageStatus =
        (message as ConfigModelAppStatus).transform()

    operator fun not() = BindAppKeyToModel(dst, elementAddress, modelId, appKeyIndex)
}