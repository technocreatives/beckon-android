package com.technocreatives.beckon.dfu

import arrow.core.Either
import com.technocreatives.beckon.dfu.internal.DfuErrorType
import com.technocreatives.beckon.dfu.internal.DfuProgressEvent
import kotlinx.coroutines.flow.StateFlow

interface DfuProcess {
    val dfuState: StateFlow<DfuState>

    suspend fun abort(): Either<AbortError, Unit>
}

sealed interface AbortError {
    object AlreadyAborted : AbortError
    object Timeout : AbortError
}

sealed interface DfuState {
    fun finished(): Boolean = when (this) {
        Success, Aborted, is Failed -> true
        else -> false
    }

    // Initial connection to the state.
    object Connecting : DfuState

    object Preparing : DfuState
    data class Uploading(val percent: Int, val currentPart: Int, val totalParts: Int) : DfuState
    object Disconnected : DfuState

    object Success : DfuState
    object Aborted : DfuState
    data class Failed(val error: DfuError) : DfuState
}

sealed interface DfuError {
    object BluetoothOff : DfuError
    object DeviceDisconnected : DfuError
    data class Unknown(val error: Int, val errorType: DfuErrorType, val message: String?) : DfuError
    data class InvalidState(val state: DfuState, val event: DfuProgressEvent) : DfuError
}
