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
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.DfuServiceInitiator
import timber.log.Timber

internal class DfuClientImpl(
    private val context: Context,
    private val dfuService: Class<out DfuBaseService>
) : DfuClient {
    private val coroutineContext = Job()
    private val scope = CoroutineScope(coroutineContext)

    private val processes: MutableMap<String, DfuProcess> = mutableMapOf()

    override fun start(
        conf: DfuConfiguration
    ): Either<StartDfuError, DfuProcess> {
        DfuServiceInitiator.createDfuNotificationChannel(context)

        if (processes.containsKey(conf.macAddress)) {
            return StartDfuError.AlreadyInProgress.left()
        }

        val newProcess = DfuProcessImpl(context, conf, dfuService)

        processes[conf.macAddress] = newProcess

        scope.launch {
            val finalState = newProcess.finishedState()
            processes.remove(conf.macAddress)
        }

        Timber.d("Returning newly created process")
        return newProcess.right()
    }

    private suspend fun DfuProcess.finishedState() = dfuState.filter { !it.isRunning() }.first()
}


