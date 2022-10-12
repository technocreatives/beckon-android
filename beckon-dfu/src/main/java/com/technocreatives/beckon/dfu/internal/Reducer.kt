package com.technocreatives.beckon.dfu.internal

import com.technocreatives.beckon.dfu.DfuError
import com.technocreatives.beckon.dfu.DfuState
import no.nordicsemi.android.dfu.DfuBaseService
import timber.log.Timber

// Old FW result:
// Event: Connected
// Event: Starting
// Event: EnablingDfuMode
// Event: Disconnecting
// Event: Connecting
// Event: Connected
// Event: Starting
// Event: Disconnecting
// Event: Disconnected
// Event: Error(error=1029, errorType=DfuRemote, message=FW version failure)

// Device not reachable:
// Event: Disconnected
// Event: Error(error=133, errorType=CommunicationState, message=GATT ERROR)


// Device was already in DFU Mode:
// Event: Disconnected
// Event: Error(error=4096, errorType=Other, message=DFU DEVICE DISCONNECTED)

// Successful DFU when in DFU mode
// Event: Connected
// Event: Starting
// Event: Started
// Event: Uploading(percent=0, speed=0.0, avgSpeed=0.0, currentPart=2, totalParts=2)
// Event: Uploading(percent=1, speed=27.11111, avgSpeed=27.11111, currentPart=2, totalParts=2)
// Event: Uploading(percent=2, speed=89.89474, avgSpeed=39.782608, currentPart=2, totalParts=2)
// Event: Uploading(percent=3, speed=4.203046, avgSpeed=10.960825, currentPart=2, totalParts=2)
// Event: Uploading(percent=4, speed=61.0, avgSpeed=13.692008, currentPart=2, totalParts=2)
// Event: Uploading(percent=5, speed=4.460094, avgSpeed=9.503727, currentPart=2, totalParts=2)
// ...
// Event: Uploading(percent=95, speed=71.166664, avgSpeed=8.967265, currentPart=2, totalParts=2)
// Event: Uploading(percent=96, speed=4.5673075, avgSpeed=8.870721, currentPart=2, totalParts=2)
// Event: Uploading(percent=97, speed=74.26087, avgSpeed=8.949952, currentPart=2, totalParts=2)
// Event: Uploading(percent=98, speed=122.0, avgSpeed=9.03327, currentPart=2, totalParts=2)
// Event: Uploading(percent=99, speed=3.869159, avgSpeed=8.919481, currentPart=2, totalParts=2)
// Event: Uploading(percent=100, speed=45.263157, avgSpeed=8.990443, currentPart=2, totalParts=2)
// Event: Disconnecting
// Event: Disconnected
// Event: Complete

internal val reducer: suspend ((DfuState, DfuProgressEvent) -> DfuState) =
    { prevState: DfuState, event: DfuProgressEvent ->
        val newState = when (prevState) {
            is DfuState.Starting -> prevState.reduce(event)
            is DfuState.EnablingBootloader -> prevState.reduce(event)
            is DfuState.Uploading -> prevState.reduce(event)
            is DfuState.Success -> prevState
            is DfuState.Aborted -> prevState
            is DfuState.Failed -> prevState
        }
        Timber.d("PrevState: $prevState")
        Timber.d("Event: $event")
        Timber.d("NewState: $newState")
        newState
    }

private fun DfuState.Starting.reduce(event: DfuProgressEvent): DfuState {
    return when (event) {
        DfuProgressEvent.EnablingDfuMode -> DfuState.EnablingBootloader
        is DfuProgressEvent.Uploading -> DfuState.Uploading(
            event.percent,
            event.currentPart,
            event.totalParts
        )

        DfuProgressEvent.Abort -> DfuState.Aborted
        DfuProgressEvent.Complete -> DfuState.Failed(DfuError.InvalidState(this, event))
        is DfuProgressEvent.Error -> event.toDfuStateError()

        else -> this
    }
}

private fun DfuState.EnablingBootloader.reduce(event: DfuProgressEvent): DfuState {
    return when (event) {
        is DfuProgressEvent.Uploading -> DfuState.Uploading(
            event.percent,
            event.currentPart,
            event.totalParts
        )

        DfuProgressEvent.EnablingDfuMode,
        DfuProgressEvent.Complete -> DfuState.Failed(DfuError.InvalidState(this, event))
        DfuProgressEvent.Abort -> DfuState.Aborted
        is DfuProgressEvent.Error -> event.toDfuStateError()
        else -> this
    }
}

private fun DfuState.Uploading.reduce(event: DfuProgressEvent): DfuState {
    return when (event) {
        is DfuProgressEvent.Uploading -> DfuState.Uploading(
            event.percent,
            event.currentPart,
            event.totalParts
        )

        DfuProgressEvent.Complete -> DfuState.Success
        DfuProgressEvent.Abort -> DfuState.Aborted
        is DfuProgressEvent.Error -> event.toDfuStateError()

        DfuProgressEvent.Starting,
        DfuProgressEvent.Started,
        DfuProgressEvent.EnablingDfuMode -> DfuState.Failed(DfuError.InvalidState(this, event))
        else -> this
    }
}

private fun DfuProgressEvent.Error.toDfuStateError(): DfuState.Failed {
    val error = when (error) {
        DfuBaseService.ERROR_BLUETOOTH_DISABLED -> DfuError.BluetoothOff
        DfuBaseService.ERROR_DEVICE_DISCONNECTED -> DfuError.DeviceDisconnected
        else -> DfuError.Unknown(this)
    }

    return DfuState.Failed(error)
}
