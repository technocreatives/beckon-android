package com.technocreatives.beckon.dfu

import no.nordicsemi.android.dfu.DfuBaseService

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