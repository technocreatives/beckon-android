package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import androidx.annotation.IntRange
import com.squareup.moshi.JsonClass
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.UUID

typealias MacAddress = String

data class DeviceFilter(
    val name: String? = null,
    val address: String? = null,
    val serviceUuid: String? = null
) {
    fun filter(device: BluetoothDevice): Boolean {
        return filterMacAddress(device) && filterName(device)
    }

    private fun filterName(device: BluetoothDevice): Boolean {
        return if (name == null) true
        else device.name == name
    }

    private fun filterMacAddress(device: BluetoothDevice): Boolean {
        return if (address == null) true
        else device.address == address
    }
}

data class ScannerSetting(
    val settings: ScanSettings,
    val filters: List<DeviceFilter>,
    val useFilter: Boolean
)

@JsonClass(generateAdapter = true)
data class Requirement(
    val uuid: UUID,
    val service: UUID,
    val property: Property
) // mandatory characteristic

@JsonClass(generateAdapter = true)
data class Descriptor(
    val requirements: List<Requirement> = emptyList(),
    val subscribes: List<Characteristic> = emptyList(),
    val reads: List<Characteristic> = emptyList(),
    val actionsOnConnected: List<BleAction> = emptyList()
)


sealed class BleAction {
    data class RequestMTU(@IntRange(from = 23, to = 517) val mtu: Int): BleAction()
    data class Read(val characteristic: Characteristic): BleAction()
    data class Subscribe(val characteristic: Characteristic): BleAction()
}

data class NewDescriptor(
    val requirements: List<Requirement> = emptyList(),
    val ActionsOnConnected: List<BleAction> = emptyList()
)

data class ScanResult(internal val device: BluetoothDevice, val rssi: Int, val scanRecord: ScanRecord?) {
    val macAddress: String = device.address
    val name: String? = device.name
}

internal sealed class BleConnectionState {
    object NotStarted : BleConnectionState()
    object Disconnecting : BleConnectionState()
    object Disconnected : BleConnectionState()
    object Connected : BleConnectionState()
    object NotSupported : BleConnectionState()
    object Ready : BleConnectionState()
    object Connecting : BleConnectionState()
    class Failed(val message: String, val errorCode: Int) : BleConnectionState()
}

sealed class ConnectionState {
    object NotConnected : ConnectionState()
    object Disconnecting : ConnectionState()
    object Connected : ConnectionState()
    object Connecting : ConnectionState()

    override fun toString(): String {
        return when (this) {
            is NotConnected -> "ConnectionState.NotConnected"
            is Disconnecting -> "ConnectionState.Disconnecting"
            is Connected -> "ConnectionState.Connected"
            is Connecting -> "ConnectionState.Connecting"
        }
    }
}

// should be parallel with BluetoothDevice.bondState()
sealed class BondState {
    object NotBonded : BondState()
    object CreatingBond : BondState()
    object RemovingBond : BondState()
    object Bonded : BondState()

    override fun toString(): String {
        return when (this) {
            is NotBonded -> "BondState.NotBonded"
            is CreatingBond -> "BondState.CreatingBond"
            is RemovingBond -> "BondState.RemovingBond"
            is Bonded -> "BondState.Bonded"
        }
    }
}

@JsonClass(generateAdapter = true)
data class Characteristic(val uuid: UUID, val service: UUID) {
    fun toRequirement(property: Property): Requirement {
        return Requirement(uuid, service, property)
    }
}

data class Change(val uuid: UUID, val data: Data)
typealias State = Map<UUID, Data>

operator fun State.plus(change: Change): State {
    return this + (change.uuid to change.data)
}

enum class Property {
    WRITE,
    NOTIFY,
    READ
}

typealias CharacteristicMapper<T> = (Change) -> T

enum class BluetoothState {
    UNKNOWN,
    TURNING_ON,
    TURNING_OFF,
    ON,
    OFF;
}

data class BeckonState<State>(
    val metadata: Metadata,
    val connectionState: ConnectionState,
    val state: State
)
