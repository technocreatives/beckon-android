package com.technocreatives.beckon.dfu.internal

import android.content.Context
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.dfu.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import timber.log.Timber

class DfuProcessImpl(
    private val context: Context,
    private val conf: DfuConfiguration,
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
            .setZip(conf.firmware)
            .setDeviceName(conf.name)
            .setForeground(true)
            .setDisableNotification(conf.customNotifications)
            .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(conf.unsafeExperimentalButtonlessServiceInSecureDfuEnabled)
            .setNumberOfRetries(conf.numberOfRetries)

        val dfuEvents = dfuProgressEvents(context, conf.macAddress).onStart {
            dfuServiceController = initiator.start(context, conf.dfuService)
        }

        scope.launch {
            dfuEvents.scan(initialState, reducer)
                .collect(_mutableDfuState)
            Timber.d("Dfu state collection is complete")
        }
    }

    override suspend fun abort(): Either<AbortError, Unit> {
        if (dfuServiceController.isAborted) {
            return AbortError.AlreadyAborted.left()
        }

        dfuServiceController.abort()

        return withTimeoutOrNull(30000) {
            dfuState.filter {
                it is DfuState.Aborted || it.isDisconnectError()
            }.first()
            Unit.right()
        } ?: AbortError.Timeout.left()
    }

    private fun DfuState.isDisconnectError(): Boolean =
        this is DfuState.Failed && error == DfuError.DeviceDisconnected
}