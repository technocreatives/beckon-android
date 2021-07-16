package com.technocreatives.beckon.mesh

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.identity
import com.technocreatives.beckon.*
import com.technocreatives.beckon.extensions.changes
import com.technocreatives.beckon.extensions.subscribe
import com.technocreatives.beckon.mesh.MeshConstants.maxMtu
import com.technocreatives.beckon.mesh.callbacks.MeshNetworkCallbacks
import com.technocreatives.beckon.mesh.callbacks.MessageStatusCallbacks
import com.technocreatives.beckon.mesh.callbacks.ProvisioningStatusCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class BeckonMesh(private val meshManager: BeckonMeshManagerApi): CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() =  job

    private val networkCallbacks = MeshNetworkCallbacks { Mtu(69)}
    private val provisioningCallbacks = ProvisioningStatusCallbacks()
    private val messageCallbacks = MessageStatusCallbacks()

    init {
        meshManager.setMeshManagerCallbacks(networkCallbacks)
        meshManager.setProvisioningStatusCallbacks(provisioningCallbacks)
        meshManager.setMeshStatusCallbacks(messageCallbacks)
    }

    suspend fun BeckonClient.connectForProvisioning(scanResult: ScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult, ConnectionPhase.Provisioning)

    suspend fun BeckonClient.connectForProxy(scanResult: ScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult, ConnectionPhase.Proxy)

    fun register() {
        launch {
            networkCallbacks.status().collect {

            }
        }
    }

    private suspend fun BeckonClient.meshConnect(
        scanResult: ScanResult,
        method: ConnectionPhase
    ): Either<BeckonError, BeckonDevice> =
        either {
            val descriptor = Descriptor()
            val beckonDevice = connect(scanResult, descriptor).bind()
            val mtu = beckonDevice.requestMtu(maxMtu).bind()
            beckonDevice.subscribe(method.dataOutCharacteristic()).bind()
            beckonDevice.handleNotifications(method.dataOutCharacteristic())
            beckonDevice
        }


    private suspend fun BeckonDevice.handleNotifications(characteristic: Characteristic) {
        changes(MeshConstants.MESH_PROVISIONING_DATA_OUT, ::identity)
            .onEach {
                Timber.w("Device changes $it")
            }
            .collect {
                Timber.d("OnDataReceived: ${metadata().macAddress} ${it.data.value?.size}")
                meshManager.handleNotifications(
                    mtu().maximumPacketSize(),
                    it.data.value!!
                )
            }
    }

}
