package com.technocreatives.beckon.dfu.internal

import com.technocreatives.beckon.dfu.DfuError
import com.technocreatives.beckon.dfu.DfuState
import no.nordicsemi.android.dfu.DfuBaseService
import timber.log.Timber

internal val reducer: suspend ((DfuState, DfuProgressEvent) -> DfuState) =
    { prevState: DfuState, event: DfuProgressEvent ->
        val newState = when (prevState) {
            is DfuState.Connecting -> prevState.reduce(event)
            is DfuState.Preparing -> prevState.reduce(event)
            is DfuState.Uploading -> prevState.reduce(event)
            is DfuState.Success -> prevState
            is DfuState.Aborted -> prevState
            is DfuState.Failed -> prevState
            DfuState.Disconnected -> prevState.reduce(event)
        }
        Timber.d("Event: $event, prevState: $prevState")
        Timber.d("NewState: $newState")
        newState
    }

private fun DfuState.Connecting.reduce(event: DfuProgressEvent): DfuState {
    return when (event) {
        DfuProgressEvent.NotStarted -> DfuState.Connecting
        DfuProgressEvent.Connecting,
        DfuProgressEvent.Connected,
        DfuProgressEvent.Starting,
        DfuProgressEvent.Started,
        DfuProgressEvent.EnablingDfuMode,
        DfuProgressEvent.FirmwareValidating -> DfuState.Preparing
        DfuProgressEvent.Disconnecting,
        DfuProgressEvent.Disconnected -> this
        is DfuProgressEvent.Uploading,
        DfuProgressEvent.Complete -> DfuState.Failed(DfuError.InvalidState(this, event))
        DfuProgressEvent.Abort -> DfuState.Aborted
        is DfuProgressEvent.Error -> event.toDfuStateError()
    }
}

private fun DfuState.Preparing.reduce(event: DfuProgressEvent): DfuState {
    return when (event) {
        DfuProgressEvent.Connecting,
        DfuProgressEvent.Connected,
        DfuProgressEvent.Starting,
        DfuProgressEvent.Started,
        DfuProgressEvent.EnablingDfuMode,
        DfuProgressEvent.FirmwareValidating -> DfuState.Preparing
        DfuProgressEvent.Disconnecting,
        DfuProgressEvent.Disconnected -> this
        is DfuProgressEvent.Uploading -> DfuState.Uploading(
            event.percent,
            event.currentPart,
            event.totalParts
        )
        DfuProgressEvent.Complete,
        DfuProgressEvent.NotStarted -> DfuState.Failed(DfuError.InvalidState(this, event))
        DfuProgressEvent.Abort -> DfuState.Aborted
        is DfuProgressEvent.Error -> event.toDfuStateError()
    }
}

private fun DfuState.Uploading.reduce(event: DfuProgressEvent): DfuState {
    return when (event) {
        DfuProgressEvent.Disconnecting,
        DfuProgressEvent.Disconnected -> {
            if (currentPart < totalParts) {
                DfuState.Disconnected
            }
            if (percent == 100) {
                this
            } else {
                DfuState.Failed(DfuError.DeviceDisconnected)
            }
        }
        is DfuProgressEvent.Uploading -> DfuState.Uploading(
            event.percent,
            event.currentPart,
            event.totalParts
        )
        DfuProgressEvent.FirmwareValidating,
        DfuProgressEvent.Connecting -> if (currentPart < totalParts && percent == 100) {
            DfuState.Connecting
        } else {
            DfuState.Failed(DfuError.InvalidState(this, event))
        }
        DfuProgressEvent.Connected,
        DfuProgressEvent.Starting,
        DfuProgressEvent.Started,
        DfuProgressEvent.EnablingDfuMode,
        DfuProgressEvent.NotStarted -> DfuState.Failed(DfuError.InvalidState(this, event))
        DfuProgressEvent.Complete -> DfuState.Success
        DfuProgressEvent.Abort -> DfuState.Aborted
        is DfuProgressEvent.Error -> event.toDfuStateError()
    }
}

private fun DfuState.reduce(event: DfuProgressEvent): DfuState {
    return when (event) {
        DfuProgressEvent.Connecting,
        DfuProgressEvent.Connected,
        DfuProgressEvent.Starting,
        DfuProgressEvent.Started,
        DfuProgressEvent.EnablingDfuMode,
        DfuProgressEvent.FirmwareValidating -> DfuState.Preparing
        DfuProgressEvent.Disconnecting,
        DfuProgressEvent.Disconnected -> this
        is DfuProgressEvent.Uploading -> DfuState.Uploading(
            event.percent,
            event.currentPart,
            event.totalParts
        )
        DfuProgressEvent.Complete,
        DfuProgressEvent.NotStarted -> DfuState.Failed(DfuError.InvalidState(this, event))
        DfuProgressEvent.Abort -> DfuState.Aborted
        is DfuProgressEvent.Error -> event.toDfuStateError()
    }
}

private fun DfuProgressEvent.Error.toDfuStateError(): DfuState.Failed {
    val error = when (error) {
        DfuBaseService.ERROR_BLUETOOTH_DISABLED -> DfuError.BluetoothOff
        DfuBaseService.ERROR_DEVICE_DISCONNECTED -> DfuError.DeviceDisconnected
        else -> DfuError.Unknown(
            error,
            errorType,
            message
        )
    }

    return DfuState.Failed(error)
}
