package com.technocreatives.beckon.dfu

import android.content.Context
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import timber.log.Timber

class DfuClientImpl(
    private val context: Context
) : DfuClient {
    private val coroutineContext = Job()
    private val scope = CoroutineScope(coroutineContext)

    private val processes: MutableMap<String, DfuProcess> = mutableMapOf()

    override fun start(
        conf: DfuConfiguration
    ): Either<StartDfuError, DfuProcess> {
        DfuServiceInitiator.createDfuNotificationChannel(context)

        if (processes.containsKey(conf.macAddress)) {
            Timber.d("Process is already running!")
            return StartDfuError.AlreadyInProgress.left()
        }

        Timber.d("Starting DFU process")
        val newProcess = DfuProcessImpl(context, conf)

        processes[conf.macAddress] = newProcess

        Timber.d("Starting to listen for response")
        scope.launch {
            val finalState = newProcess.finishedState()
            Timber.d("DFU for ${conf.macAddress} finished with: $finalState")
            processes.remove(conf.macAddress)
        }

        Timber.d("Returning newly created process")
        return newProcess.right()
    }

    private suspend fun DfuProcess.finishedState() = dfuState.filter { it.finished() }.first()
}

sealed interface AbortError {
    object AlreadyAborted : AbortError
    object Timeout : AbortError
}

sealed interface StartDfuError {
    object AlreadyInProgress : StartDfuError
}

class DfuProcessImpl(
    private val context: Context,
    private val conf: DfuConfiguration,
) : DfuProcess {
    private val coroutineContext = Job()
    private val scope = CoroutineScope(coroutineContext)

    private val dfuProgressListener = dfuProgressEvents(context, conf.macAddress)
    private lateinit var dfuServiceController: DfuServiceController

    private val initialState: DfuState = DfuState.Connecting

    private val _mutableDfuState: MutableStateFlow<DfuState> = MutableStateFlow(initialState)
    override val dfuState: StateFlow<DfuState> = _mutableDfuState

    init {
        val initiator = DfuServiceInitiator(conf.macAddress)
            .setKeepBond(conf.keepBond)
            .setZip(conf.firmware)
            .setDeviceName(conf.name)
            .setForeground(true)
            .setDisableNotification(conf.customNotifications)
            .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(conf.unsafeExperimentalButtonlessServiceInSecureDfuEnabled)
            .setNumberOfRetries(conf.numberOfRetries)

        val dfuStream = dfuProgressEvents(context, conf.macAddress).onStart {
            dfuServiceController = initiator.start(context, conf.dfuService)
        }

        Timber.d("Starting SCAN")
        scope.launch {
            dfuStream.scan(initialState, reducer)
                .collect(_mutableDfuState)
            Timber.d("Collection is complete")
        }
        Timber.d("Starting SCAN COMPLETE")
    }

    override suspend fun abort(): Either<AbortError, Unit> {
        if (dfuServiceController.isAborted) {
            return AbortError.AlreadyAborted.left()
        }

        dfuServiceController.abort()

        return withTimeoutOrNull(10000) {
            dfuState.filter {
                it is DfuState.Aborted || it.isDisconnectError()
            }.first()
            Unit.right()
        } ?: AbortError.Timeout.left()
    }

    private fun DfuState.isDisconnectError(): Boolean =
        this is DfuState.Failed && error == DfuError.DeviceDisconnected
}

val reducer: suspend ((DfuState, DfuProgressEvent) -> DfuState) =
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

fun DfuState.Connecting.reduce(event: DfuProgressEvent): DfuState {
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

fun DfuState.Preparing.reduce(event: DfuProgressEvent): DfuState {
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

fun DfuState.Uploading.reduce(event: DfuProgressEvent): DfuState {
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
        else -> DfuError.Other(this)
    }

    return DfuState.Failed(error)
}
