package com.technocreatives.beckon.util

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.MainThread
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.Date
import java.util.UUID

abstract class BeckonClient {

    companion object {

        // return singleton instance of client
        fun create(context: Context): BeckonClient = TODO()
    }

    // scan available devices
    @MainThread
    abstract fun scan(setting: ScannerSetting): Observable<BeckonScanResult>

    @MainThread
    abstract fun scanList(setting: ScannerSetting): Observable<List<BeckonScanResult>>

    abstract fun getDevice(macAddress: String): BeckonDevice?

    abstract fun devices(): Observable<List<BeckonDevice>>
    abstract fun getDevices(): List<BeckonDevice>

    // ??? need a better interface
    abstract fun states(): Observable<List<Pair<String, Change>>>

    abstract fun saveDevices(devices: List<BeckonDevice>): Single<Boolean>

    abstract fun connect(
        result: BeckonScanResult,
        characteristics: List<Characteristic>,
        autoConnect: Boolean
    ): Observable<ConnectionState>

    abstract fun disconnect(device: BeckonDevice): Observable<ConnectionState>

    // fun states(device: Device): Observable<State>
}

interface BeckonDevice {
    fun macAddress(): String
    fun name(): String

    fun connectionState(): ConnectionState

    fun changes(): Observable<Change>

    fun write(characteristic: Characteristic, data: Data): Observable<Change>

    fun states(): Observable<DeviceState>

    fun currentStates(): List<Change>

    fun connect(autoConnect: Boolean): Observable<ConnectionState> // [Connecting, connected, Failed, complete]
}

typealias DeviceState = Pair<ConnectionState, Change>

data class Change(val key: UUID, val data: Data)

data class BeckonScanResult(private val device: BluetoothDevice, val rssi: Int)

sealed class ConnectionState {
    object NotStarted : ConnectionState()
    object Disconnecting : ConnectionState()
    object Disconnected : ConnectionState()
    object Connected : ConnectionState()
    object Connecting : ConnectionState()
    class Failed(val error: Throwable) : ConnectionState()
}

data class ScannerSetting(
    val settings: ScanSettings,
    val filters: List<ScanFilter>
)

data class Characteristic(val uuid: UUID, val service: UUID, val notify: Boolean)
// user needs to be implemented

fun scanAndConnect(context: Context, setting: ScannerSetting) {
    val client = BeckonClient.create(context)

    client.scan(setting)
        .flatMap { client.connect(it, emptyList(), true) }
        .subscribe() // render list of scanned devices
}

// can be used onClick
fun connectToADevice(client: BeckonClient, result: BeckonScanResult, characteristics: List<Characteristic>): Observable<ConnectionState> {
    return client.connect(result, characteristics, autoConnect = true)
}

// on background service
// fun run(context: Context) {
    // val client = BeckonClient.create(context)
    // client.states()
        // .subscribe {
            // showNotification();
        // }
// }

// on ui -- device detail screen
fun states(client: BeckonClient, macAddress: String, mapper: CharacteristicMapper<AxkidChange>, reducer: BiFunction<AxkidState, AxkidChange, AxkidState>): Observable<AxkidState> {

    val defaultState = AxkidState(SeatedState.Unseated, 22, 1L)
    val device = client.getDevice(macAddress) ?: return Observable.empty()

    return device
        .changes()
        .map { mapper(it) }
        .scan(defaultState, reducer)
}

const val serviceUUID = "0000fff0-0000-1000-8000-00805f9b34fb"
const val seatUuId = "0000fff3-0000-1000-8000-00805f9b34fb"
const val temperatureUuid = "0000fff2-0000-1000-8000-00805f9b34fb"

typealias CharacteristicMapper<T> = (Change) -> T
typealias Reducer<Change, State> = (Change, State) -> State

//
sealed class AxkidChange {
    class Seated(val isSeated: Boolean) : AxkidChange()
    class Temperature(val value: Int) : AxkidChange()
    object UFO : AxkidChange()
}

data class AxkidState(val seatedState: SeatedState, val temperature: Int, val created: Long)
sealed class SeatedState {
    object Unseated : SeatedState()
    class Seated(val time: Long) : SeatedState()
}

val mapper: CharacteristicMapper<AxkidChange> = {
    if (seatUuId.equals(it.key)) {
        AxkidChange.Seated(true)
    } else if (temperatureUuid.equals(it.key)) {
        AxkidChange.Temperature(30)
    } else {
        AxkidChange.UFO
    }
}

val reducer = BiFunction<AxkidState, AxkidChange, AxkidState> { state, changes ->
    when (changes) {
        is AxkidChange.Seated -> {
            if (changes.isSeated) {
                state.copy(seatedState = SeatedState.Seated(time = Date().time))
            } else {
                state.copy(seatedState = SeatedState.Unseated)
            }
        }
        is AxkidChange.Temperature -> {
            state.copy(temperature = changes.value)
        }
        is AxkidChange.UFO -> state
    }
}
