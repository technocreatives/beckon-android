package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.mesh.BeckonMesh
import com.technocreatives.beckon.mesh.BeckonMeshManagerApi
import com.technocreatives.beckon.mesh.MeshConstants
import com.technocreatives.beckon.mesh.extensions.onDisconnect
import com.technocreatives.beckon.mesh.model.NetworkKey
import com.technocreatives.beckon.mesh.processor.MessageQueue
import com.technocreatives.beckon.mesh.processor.Pdu
import com.technocreatives.beckon.mesh.processor.PduSender
import com.technocreatives.beckon.mesh.processor.PduSenderResult
import kotlinx.coroutines.Job
import no.nordicsemi.android.mesh.transport.MeshMessage

class KeyRefresh(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val beckonDevice: BeckonDevice
) : MeshState(beckonMesh, meshApi) {
    private var queue: MessageQueue = MessageQueue(object : PduSender {
        override fun createPdu(
            dst: Int,
            meshMessage: MeshMessage
        ) = meshApi.createPdu(dst, meshMessage)

        override suspend fun sendPdu(pdu: Pdu): PduSenderResult =
            with(meshApi) {
                beckonDevice.sendPdu(pdu.data, MeshConstants.proxyDataInCharacteristic)
            }
    })

    private var disconnectJob: Job? = null

    init {
        meshApi.setMeshManagerCallbacks(
            ConnectedMeshManagerCallbacks(
                beckonDevice,
                meshApi,
                queue
            )
        )
        meshApi.setMeshStatusCallbacks(
            ConnectedMessageStatusCallbacks(
                meshApi,
                queue
            )
        )
        with(queue) {
            beckonMesh.execute()
        }
        disconnectJob = beckonMesh.execute {
            beckonDevice.onDisconnect {
                beckonMesh.updateState(Loaded(beckonMesh, meshApi))
            }
        }
    }

    override suspend fun isValid(): Boolean = beckonMesh.isCurrentState<KeyRefresh>()

    suspend fun distributeNetKey(key: NetworkKey, newKey: ByteArray) {
        val updatedKey = Either.catch {
             meshApi.meshNetwork().distributeNetKey(key.actualKey, newKey)
        }
    }
}

