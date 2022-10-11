package com.technocreatives.beckon.dfu.internal

import no.nordicsemi.android.dfu.DfuBaseService

sealed class DfuProgressEvent {
    object Connected : DfuProgressEvent()
    object Starting : DfuProgressEvent()
    object EnablingDfuMode : DfuProgressEvent()
    object Started : DfuProgressEvent()
    object Connecting : DfuProgressEvent()

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

sealed class DfuErrorType {
    object Other : DfuErrorType()
    object CommunicationState : DfuErrorType()
    object Communication : DfuErrorType()
    object DfuRemote : DfuErrorType()

    companion object {
        fun fromInt(errorType: Int): DfuErrorType {
            return when (errorType) {
                DfuBaseService.ERROR_TYPE_OTHER -> Other
                DfuBaseService.ERROR_TYPE_COMMUNICATION_STATE -> CommunicationState
                DfuBaseService.ERROR_TYPE_COMMUNICATION -> Communication
                DfuBaseService.ERROR_TYPE_DFU_REMOTE -> DfuRemote
                else -> throw RuntimeException("Unsupported error type $errorType")
            }
        }
    }

    override fun toString(): String = javaClass.simpleName
}
