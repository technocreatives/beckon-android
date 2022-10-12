package com.technocreatives.beckon.dfu.internal

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onEach
import no.nordicsemi.android.dfu.DfuProgressListener
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import timber.log.Timber

internal fun dfuProgressEvents(context: Context, macAddress: String): Flow<DfuProgressEvent> {
    var listener: DfuProgressListener
    return callbackFlow {
        listener = object : DfuProgressListener {
            override fun onDeviceConnecting(deviceAddress: String) {
                trySend(DfuProgressEvent.Connecting)
            }

            override fun onDfuProcessStarted(deviceAddress: String) {
                trySend(DfuProgressEvent.Started)
            }

            override fun onDeviceDisconnecting(deviceAddress: String?) {
                trySend(DfuProgressEvent.Disconnecting)
            }

            override fun onDeviceDisconnected(deviceAddress: String) {
                trySend(DfuProgressEvent.Disconnected)
            }

            override fun onDeviceConnected(deviceAddress: String) {
                trySend(DfuProgressEvent.Connected)
            }

            override fun onEnablingDfuMode(deviceAddress: String) {
                trySend(DfuProgressEvent.EnablingDfuMode)
            }

            override fun onFirmwareValidating(deviceAddress: String) {
                trySend(DfuProgressEvent.FirmwareValidating)
            }

            override fun onDfuProcessStarting(deviceAddress: String) {
                trySend(DfuProgressEvent.Starting)
            }

            override fun onProgressChanged(
                deviceAddress: String,
                percent: Int,
                speed: Float,
                avgSpeed: Float,
                currentPart: Int,
                partsTotal: Int
            ) {
                trySend(
                    DfuProgressEvent.Uploading(
                        percent,
                        speed,
                        avgSpeed,
                        currentPart,
                        partsTotal
                    )
                )
            }

            override fun onDfuCompleted(deviceAddress: String) {
                trySend(DfuProgressEvent.Complete)
                channel.close()
            }

            override fun onDfuAborted(deviceAddress: String) {
                trySend(DfuProgressEvent.Abort)
                channel.close()
            }

            override fun onError(
                deviceAddress: String,
                error: Int,
                errorType: Int,
                message: String?
            ) {
                trySend(
                    DfuProgressEvent.Error(
                        error,
                        DfuErrorType.fromInt(errorType),
                        message
                    )
                )
                channel.close()
            }
        }

        Timber.d("registerProgressListener")
        DfuServiceListenerHelper.registerProgressListener(
            context,
            listener,
            macAddress
        )

        awaitClose {
            Timber.d("unregisterProgressListener")
            DfuServiceListenerHelper.unregisterProgressListener(
                context,
                listener
            )
        }
    }.onEach {
        Timber.d("DfuProgressEvent: $it")
    }
}