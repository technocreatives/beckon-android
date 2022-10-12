package com.technocreatives.beckon.dfu.internal

import android.content.Context
import arrow.core.Either
import arrow.core.continuations.either
import com.technocreatives.beckon.dfu.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import timber.log.Timber

class DfuProcessImpl(
    private val context: Context,
    conf: DfuConfiguration,
    private val dfuService: Class<out DfuBaseService>
) : DfuProcess {
    private val coroutineContext = Job()
    private val scope = CoroutineScope(coroutineContext)

    // Controller for the DFU Service
    private lateinit var dfuServiceController: DfuServiceController

    private val initialState: DfuState = DfuState.Starting
    private val _mutableDfuState: MutableStateFlow<DfuState> = MutableStateFlow(initialState)
    override val dfuState: StateFlow<DfuState> = _mutableDfuState

    init {
        val initiator = DfuServiceInitiator(conf.macAddress)
            .setKeepBond(conf.keepBond)
            .setDeviceName(conf.name)
            .setFirmware(conf.firmware)
            .setForeground(conf.foreground)
            .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(conf.unsafeExperimentalButtonlessServiceInSecureDfuEnabled)
            .setDisableNotification(conf.customNotifications)
            .setNumberOfRetries(conf.numberOfRetries)

        val dfuEvents = dfuProgressEvents(context, conf.macAddress).onStart {
            dfuServiceController = initiator.start(context, dfuService)
        }

        scope.launch {
            dfuEvents.scan(initialState, reducer)
                .collect(_mutableDfuState)
            Timber.d("Dfu state collection is complete")
        }
    }

    override suspend fun abort(): Either<AbortError, Unit> = either {
        ensure(dfuServiceController.isAborted) { AbortError.AlreadyAborted }
        ensure(!dfuState.value.isRunning()) { AbortError.AlreadyFinished }

        dfuServiceController.abort()

        withTimeoutOrNull(30000) {
            dfuState.filter {
                it is DfuState.Aborted || it.isDisconnectError()
            }.first()
        } ?: shift(AbortError.Timeout)
    }

    private fun DfuState.isDisconnectError(): Boolean =
        this is DfuState.Failed && error == DfuError.DeviceDisconnected
}

private fun DfuServiceInitiator.setFirmware(firmware: Firmware) =
    when(firmware) {
        is Firmware.Path -> setZip(firmware.path)
        is Firmware.Uri -> setZip(firmware.uri)
        is Firmware.AndroidRes -> setZip(firmware.resource)
    }