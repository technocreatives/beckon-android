package com.technocreatives.beckon.dfu

sealed class DfuProgressEvent {
    object NotStarted : DfuProgressEvent()
    object Connecting : DfuProgressEvent()
    object Connected : DfuProgressEvent()
    object Starting : DfuProgressEvent()
    object Started : DfuProgressEvent()
    object Disconnecting : DfuProgressEvent()
    object Disconnected : DfuProgressEvent()

    object FirmwareValidating : DfuProgressEvent()

    data class Uploading(
        val percent: Int,
        val speed: Float,
        val avgSpeed: Float,
        val currentPart: Int,
        val totalParts: Int
    ) :
        DfuProgressEvent()

    object EnablingDfuMode : DfuProgressEvent()
    object Complete : DfuProgressEvent()
    object Abort : DfuProgressEvent()
    data class Error(val error: Int, val errorType: DfuErrorType, val message: String?) :
        DfuProgressEvent()

    override fun toString(): String = when (this) {
        is Uploading -> super.toString()
        is Error -> super.toString()
        else -> javaClass.simpleName
    }
}