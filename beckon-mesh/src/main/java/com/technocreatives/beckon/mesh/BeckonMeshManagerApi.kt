package com.technocreatives.beckon.mesh

import android.annotation.SuppressLint
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
import no.nordicsemi.android.mesh.NetworkKey
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.*
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

    private val networkCallbacks by lazy {
        MeshNetworkCallbacks({ queryMtu() },
            { mn ->
                if (mn != null)
                    runBlocking {
                        state.update {
                            MState.Loaded(mn)
                        }
                    }
                networkLoadingEmitter.complete(Unit.right())
            },
            { error ->
                runBlocking {
                    state.update {
                        MState.LoadFailed(error)
                    }
                }
                networkLoadingEmitter.complete(MeshLoadFailedError(error).left())
            },
            { unprovisionedMeshNode, pdu ->
                // sendProvisioningPdu
                Timber.d("sendPdu - sendProvisioningPdu - ${pdu.size}")
                currentBeckonDevice()?.let {
                    runBlocking {
                        it.sendPdu(pdu, MeshConstants.provisioningDataInCharacteristic).fold(
                            { Timber.w("SendPdu error: $it") },
                            { Timber.d("sendPdu success") }
                        )
                    }
                }
            },
            { pdu ->
                // onMeshPduCreated
                Timber.d("sendPdu - onMeshPduCreated - ${pdu.size}")
                currentBeckonDevice()?.let {
                    runBlocking {
                        it.sendPdu(pdu, MeshConstants.proxyDataInCharacteristic).fold(
                            { Timber.w("SendPdu error: $it") },
                            { Timber.d("sendPdu success") }
                        )
                    }
                }
            }
        )
    }

    // todo handle
    internal var beckonDevice: BeckonDevice? = null
    private fun currentBeckonDevice(): BeckonDevice? = beckonDevice

    private val provisioningCallbacks = ProvisioningStatusCallbacks()
    private val messageCallbacks = MessageStatusCallbacks()


    private lateinit var state: Atomic<MState>

    init {
        setMeshManagerCallbacks(networkCallbacks)
        setProvisioningStatusCallbacks(provisioningCallbacks)
        setMeshStatusCallbacks(messageCallbacks)
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


    @SuppressLint("RestrictedApi")
    suspend fun register() {
        state = Atomic(MState.Unloaded)

        // todo fix
        launch {
            networkCallbacks.status().collect {
                Timber.d("Mesh Manager api $it")
                onMeshStatusChange(it)
            }

            messageCallbacks.status()
                .filterIsInstance<MessageStatus.MeshMessageReceived>()
                .collect {
                    val meshNetwork = meshNetwork
                    val node = meshNetwork?.getNode(it.src)!!
                    if (it.meshMessage.opCode == ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt()) {
                        Timber.d("onMessageReceived CONFIG_COMPOSITION_DATA_STATUS:")
                        GlobalScope.launch {
                            delay(500)

                            val configDefaultTtlGet = ConfigDefaultTtlGet()
                            createMeshPdu(
                                2, //TODO node.getUnicastAddress(),
                                configDefaultTtlGet
                            )
                        }
                    } else if (it.meshMessage.opCode == ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS) {
                        val status = it.meshMessage as ConfigDefaultTtlStatus
                        Timber.d("onMessageReceived CONFIG_DEFAULT_TTL_STATUS: $status")
                        GlobalScope.launch {
                            delay(1500)
                            val networkTransmitSet = ConfigNetworkTransmitSet(2, 1)
                            createMeshPdu(
                                2, //TODO node.getUnicastAddress(),
                                networkTransmitSet
                            )
                        }
                    } else if (it.meshMessage.opCode == ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS) {
                        Timber.d("onMessageReceived CONFIG_NETWORK_TRANSMIT_STATUS")
                        GlobalScope.launch {
                            delay(1500)
                            val appKey = meshNetwork.getAppKey(0)!!
                            val index: Int = node.addedNetKeys!!.get(0)!!.index
                            val networkKey: NetworkKey = meshNetwork.netKeys[index]
                            val configAppKeyAdd = ConfigAppKeyAdd(networkKey, appKey)
                            createMeshPdu(
                                node.unicastAddress,
                                configAppKeyAdd
                            )
                        }
                    } else if (it.meshMessage.opCode == ConfigMessageOpCodes.CONFIG_APPKEY_STATUS) {
                        val status = it.meshMessage as ConfigAppKeyStatus
                        Timber.d("onMessageReceived CONFIG_APPKEY_STATUS: $status")
                        val currentState = state.get()
                        if(currentState is MState.Provisioning) {
                            currentState.phase.completeExchangeKeys(node)
                        }
                    } else if (it.meshMessage.opCode == ConfigMessageOpCodes.CONFIG_MODEL_APP_STATUS) {

                    }
                }
        }

    }

    private suspend fun BeckonDevice.sendPdu(
        pdu: ByteArray,
        characteristic: Characteristic
    ): Either<BeckonActionError, Unit> = either {
        val splitPackage = writeSplit(pdu, characteristic).bind()
        Timber.d("onDataSend: ${splitPackage.data.value?.size}")
        handleWriteCallbacks(splitPackage.mtu, splitPackage.data.value!!)
    }

    private suspend fun onMeshStatusChange(status: MeshStatus) {
        state.update {
            when (status) {
                is MeshStatus.ImportFailed -> TODO()
                is MeshStatus.Imported -> TODO() // todo move to callback
                MeshStatus.ImportedEmpty -> TODO() // todo move to callback
                is MeshStatus.Updated -> TODO()
                MeshStatus.UpdatedEmpty -> TODO()
            }
        }
    }

    suspend fun loadNetwork(): Either<MeshLoadFailedError, Unit> {
        val currentState = state.get()

        if (currentState is MState.Unloaded) {
            loadMeshNetwork()
        } else {
            throw IllegalMeshStateError(currentState)
        }
        return networkLoadingEmitter.await()
    }

    suspend fun startProvisioning(): Either<MeshLoadFailedError, ProvisioningPhase> {
        // todo disconnect mesh if connected state
        val provisioningPhase = ProvisioningPhase(this)
        setProvisioningStatusCallbacks(provisioningPhase.provisioningStatusCallbacks)
        state.update { MState.Provisioning(provisioningPhase) }
        return provisioningPhase.right()
    }

    internal suspend fun connectForProvisioning(scanResult: ScanResult): Either<BeckonError, BeckonDevice> {
        val currentState = state.get()
        if (currentState is MState.Loaded) { // todo correct state
            return meshConnect(scanResult, ConnectionPhase.Provisioning)
        } else {
            throw IllegalMeshStateError(currentState)
        }
    }

    suspend fun connectForProxy(scanResult: ScanResult): Either<BeckonError, BeckonDevice> =
        meshConnect(scanResult, ConnectionPhase.Proxy)

    private fun sendProvisioningPdu(
        beckonDevice: BeckonDevice,
        meshNode: UnprovisionedMeshNode?,
        pdu: ByteArray?
    ) {

        Timber.d("sendPdu ${pdu?.size} ")

        launch {
            beckonDevice.writeSplit(pdu!!, MeshConstants.provisioningDataInCharacteristic).fold(
                { Timber.w("SendPdu error: $it") },
                {
                    Timber.d("onDataSend: ${it.data.value?.size}")
                    handleWriteCallbacks(
                        it.mtu,
                        it.data.value!!
                    )
                }
            )
        }

    }

    suspend fun scan(scannerSetting: ScannerSetting): Flow<Either<ScanError, List<ScanResult>>> {
        return beckonClient.scan(scannerSetting)
            .mapZ { it.sortedBy { it.macAddress } }
    }

    suspend fun stopScan() {
        beckonClient.stopScan()
    }

    internal suspend fun scanForProvisioning(): Flow<Either<ScanError, List<ScanResult>>> {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setUseHardwareFilteringIfSupported(false)
            .build()

        val scannerSettings = ScannerSetting(
            settings,
            filters = listOf(
                DeviceFilter(
                    serviceUuid = MeshConstants.MESH_PROXY_UUID.toString()
                )
            ),
            useFilter = false
        )
        return scan(scannerSettings)
    }

    private suspend fun meshConnect(
        scanResult: ScanResult,
        method: ConnectionPhase
    ): Either<BeckonError, BeckonDevice> =
        either {
            val descriptor = Descriptor()
            val beckonDevice = beckonClient.connect(scanResult, descriptor).bind()
            val mtu = beckonDevice.requestMtu(MeshConstants.maxMtu).bind()
            beckonDevice.subscribe(method.dataOutCharacteristic()).bind()
            launch { // todo is this the right way?
                beckonDevice.handleNotifications(method.dataOutCharacteristic())
            }
            beckonDevice
        }

    private suspend fun BeckonDevice.handleNotifications(characteristic: Characteristic) {
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
