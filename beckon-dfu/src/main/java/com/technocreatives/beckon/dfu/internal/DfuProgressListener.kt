package com.technocreatives.beckon.dfu.internal

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.nordicsemi.android.dfu.DfuProgressListener
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import timber.log.Timber

internal fun dfuProgressEvents(context: Context, macAddress: String): Flow<DfuProgressEvent> {
    var listener: DfuProgressListener
    return callbackFlow {
        listener = object : DfuProgressListener {
            override fun onDeviceConnecting(deviceAddress: String) {
                Timber.d("onDeviceConnecting")
                trySend(DfuProgressEvent.Connecting)
            }

            override fun onDfuProcessStarted(deviceAddress: String) {
                Timber.d("onDfuProcessStarted")
                trySend(DfuProgressEvent.Started)
            }

            override fun onDeviceDisconnecting(deviceAddress: String?) {
                Timber.d("onDeviceDisconnecting")
                trySend(DfuProgressEvent.Disconnecting)
            }

            override fun onDeviceDisconnected(deviceAddress: String) {
                Timber.d("onDeviceDisconnected")
                trySend(DfuProgressEvent.Disconnected)
            }

            override fun onDeviceConnected(deviceAddress: String) {
                Timber.d("onDeviceConnected")
                trySend(DfuProgressEvent.Connected)
            }

            override fun onEnablingDfuMode(deviceAddress: String) {
                Timber.d("onEnablingDfuMode")
                trySend(DfuProgressEvent.EnablingDfuMode)
            }

            override fun onFirmwareValidating(deviceAddress: String) {
                Timber.d("onFirmwareValidating")
                trySend(DfuProgressEvent.FirmwareValidating)
            }

            override fun onDfuProcessStarting(deviceAddress: String) {
                Timber.d("onDfuProcessStarting")
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
                Timber.d("onProgressChanged")
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
                Timber.d("onDfuCompleted")
                trySend(DfuProgressEvent.Complete)
                channel.close()
            }

            override fun onDfuAborted(deviceAddress: String) {
                Timber.d("onDfuAborted")
                trySend(DfuProgressEvent.Abort)
                channel.close()
            }

            override fun onError(
                deviceAddress: String,
                error: Int,
                errorType: Int,
                message: String?
            ) {
                Timber.d("onError")
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
    }
}