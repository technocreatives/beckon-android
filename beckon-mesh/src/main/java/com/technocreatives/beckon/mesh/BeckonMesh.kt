package com.technocreatives.beckon.mesh

import android.content.Context
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.identity
import com.technocreatives.beckon.*
import com.technocreatives.beckon.extensions.changes
import com.technocreatives.beckon.extensions.subscribe
import com.technocreatives.beckon.mesh.MeshConstants.maxMtu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class BeckonMesh(context: Context, private val beckonClient: BeckonClient) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job

    private val meshManager: BeckonMeshManagerApi =
        BeckonMeshManagerApi(context, beckonClient)
//    private val meshState: MeshState = Unloaded(meshManager, beckonClient)

    private suspend fun load(): Either<MeshLoadFailedError, Unit> {
        return meshManager.loadNetwork()
    }

    suspend fun connectForProvisioning(scanResult: ScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult, ConnectionPhase.Provisioning)

    suspend fun connectForProxy(scanResult: ScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult, ConnectionPhase.Proxy)

    private suspend fun meshConnect(
        scanResult: ScanResult,
        method: ConnectionPhase
    ): Either<BeckonError, BeckonDevice> =
        either {
            val descriptor = Descriptor()
            val beckonDevice = beckonClient.connect(scanResult, descriptor).bind()
            val mtu = beckonDevice.requestMtu(maxMtu).bind()
            beckonDevice.subscribe(method.dataOutCharacteristic()).bind()
            beckonDevice.handleNotifications(method.dataOutCharacteristic())
            beckonDevice
        }


    private fun BeckonDevice.handleNotifications(characteristic: Characteristic) {
        launch {
            changes(characteristic.uuid, ::identity)
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

}
