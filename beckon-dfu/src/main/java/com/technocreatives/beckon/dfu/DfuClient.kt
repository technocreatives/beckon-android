package com.technocreatives.beckon.dfu

import android.content.Context
import arrow.core.Either
import kotlinx.coroutines.flow.StateFlow
import no.nordicsemi.android.dfu.DfuBaseService

interface DfuClient {
    fun start(conf: DfuConfiguration): Either<StartDfuError, DfuProcess>

    companion object {
        private var client: DfuClient? = null

        fun create(context: Context): DfuClient {
            if (client != null) {
                return client!!
            }

            client = DfuClientImpl(context)
            return client!!
        }
    }
}

interface DfuProcess {
    val dfuState: StateFlow<DfuState>

    suspend fun abort(): Either<AbortError, Unit>
}

data class DfuConfiguration(
    val macAddress: String,
    val firmware: Int, // Todo change to Uri
    val dfuService: Class<out DfuBaseService>,
    val name: String? = null,
    val keepBond: Boolean = true,
    val unsafeExperimentalButtonlessServiceInSecureDfuEnabled: Boolean = true,
    val customNotifications: Boolean = false,
    val numberOfRetries: Int = 0
)

sealed interface DfuState {
    fun finished(): Boolean =
        when (this) {
            Success, Aborted, is Failed -> true
            else -> false
        }

    // Initial connection to the state.
    object Connecting : DfuState

    //
    object Preparing : DfuState
    data class Uploading(val percent: Int, val currentPart: Int, val totalParts: Int) : DfuState
    object Disconnected : DfuState

    object Success : DfuState
    object Aborted : DfuState
    data class Failed(val error: DfuError) : DfuState
}

sealed class DfuError {
    object BluetoothOff : DfuError()
    object DeviceDisconnected : DfuError()
    data class Other(val error: DfuProgressEvent.Error) : DfuError()
    data class InvalidState(val state: DfuState, val event: DfuProgressEvent) : DfuError()

    override fun toString(): String = when (this) {
        is Other, is InvalidState -> super.toString()
        else -> javaClass.simpleName
    }
}
