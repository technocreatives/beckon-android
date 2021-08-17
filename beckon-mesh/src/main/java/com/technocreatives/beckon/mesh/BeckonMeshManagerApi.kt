package com.technocreatives.beckon.mesh

import android.content.Context
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import arrow.fx.coroutines.Atomic
import com.technocreatives.beckon.*
import com.technocreatives.beckon.extensions.changes
import com.technocreatives.beckon.extensions.scan
import com.technocreatives.beckon.extensions.subscribe
import com.technocreatives.beckon.extensions.writeSplit
import com.technocreatives.beckon.mesh.callbacks.*
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import no.nordicsemi.android.mesh.MeshManagerApi
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class BeckonMeshManagerApi(
    context: Context,
    private val beckonClient: BeckonClient,
) : MeshManagerApi(context), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job

    private val networkLoadingEmitter =
        CompletableDeferred<Either<MeshLoadFailedError, Unit>>()


    private fun queryMtu(): Mtu =
        currentBeckonDevice()?.mtu() ?: Mtu(69)

//    private val networkCallbacks by lazy {
//        MeshNetworkCallbacks({ queryMtu() },
//            { mn ->
//                if (mn != null) {
//
//                    runBlocking {
//                        state.update {
//                            val loaded = MState.Loaded(mn)
//                            networkLoadingEmitter.complete(Unit.right())
//                            loaded
//                        }
//                    }
//                } else {
//                    networkLoadingEmitter.complete(MeshLoadFailedError("MeshNetwork is empty").left())
//                }
//            },
//            { error ->
//                networkLoadingEmitter.complete(MeshLoadFailedError(error).left())
//            },
//            { unprovisionedMeshNode, pdu ->
//                // sendProvisioningPdu
//                Timber.d("sendPdu - sendProvisioningPdu - ${pdu.size}")
//                currentBeckonDevice()?.let {
//                    launch {
//                        it.sendPdu(pdu, MeshConstants.provisioningDataInCharacteristic).fold(
//                            { Timber.w("SendPdu error: $it") },
//                            { Timber.d("sendPdu success") }
//                        )
//                    }
//                }
//            },
//            { pdu ->
//                // onMeshPduCreated
//                Timber.d("sendPdu - onMeshPduCreated - ${pdu.size}")
//                currentBeckonDevice()?.let {
//                    launch {
//                        // todo sending success or error signal
//                        it.sendPdu(pdu, MeshConstants.proxyDataInCharacteristic).fold(
//                            { Timber.w("SendPdu error: $it") },
//                            { Timber.d("sendPdu success") }
//                        )
//                    }
//                }
//            }
//        )
//    }

    // todo handle in a better way
    internal var beckonDevice: BeckonDevice? = null
    private fun currentBeckonDevice(): BeckonDevice? = beckonDevice

    private val provisioningCallbacks = ProvisioningStatusCallbacks()
    private val messageCallbacks = MessageStatusCallbacks()


    private lateinit var state: Atomic<MState>

    init {
//        setMeshManagerCallbacks(networkCallbacks)
//        setProvisioningStatusCallbacks(provisioningCallbacks)
//        setMeshStatusCallbacks(messageCallbacks)
    }

    private suspend fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
        if (meshNetwork != null)
            state.update {
                MState.Loaded(meshNetwork)
            }
    }

    private suspend fun onNetworkLoadFailed(error: String) {
        state.update {
            MState.LoadFailed(error)
        }
    }

    internal suspend fun setConnected(beckonDevice: BeckonDevice) {
        state.update {
            MState.Connected(beckonDevice)
        }
    }

    suspend fun BeckonDevice.sendPdu(
        pdu: ByteArray,
        characteristic: Characteristic
    ): Either<BeckonActionError, Unit> = either {
        val splitPackage = writeSplit(pdu, characteristic).bind()
        Timber.d("onDataSend: ${splitPackage.data.value?.size}")
        handleWriteCallbacks(splitPackage.mtu, splitPackage.data.value!!)
    }



    suspend fun BeckonDevice.handleNotifications(characteristic: Characteristic) {
        changes(characteristic.uuid, ::identity)
            .onEach {
                Timber.w("Device changes $it")
            }
            .collect {
                Timber.d("OnDataReceived: ${metadata().macAddress} ${it.data.value?.size}")
                handleNotifications(
                    mtu().maximumPacketSize(),
                    it.data.value!!
                )
            }
    }

}

sealed class MState {

    data class LoadFailed(val error: String) : MState()
    data class Loaded(val meshNetwork: MeshNetwork) : MState()
    object Unloaded : MState()
    data class Connected(val beckonDevice: BeckonDevice) : MState()
    data class Provisioning(val phase: ProvisioningPhase) : MState()
}
