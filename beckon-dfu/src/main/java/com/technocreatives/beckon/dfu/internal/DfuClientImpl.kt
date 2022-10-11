package com.technocreatives.beckon.dfu.internal

import android.content.Context
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.dfu.DfuClient
import com.technocreatives.beckon.dfu.DfuConfiguration
import com.technocreatives.beckon.dfu.DfuProcess
import com.technocreatives.beckon.dfu.StartDfuError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private suspend fun DfuProcess.finishedState() = dfuState.filter { !it.isRunning() }.first()
}


