package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import androidx.annotation.IntRange
import com.technocreatives.beckon.util.UuidSerializer
import kotlinx.serialization.Serializable
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.ScanRecord
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.*

typealias MacAddress = String

@JvmInline
value class Mtu(@IntRange(from = MIN, to = MAX) val value: Int) {
    fun maximumPacketSize(): Int = value - 3

    companion object {
        const val MIN = 23L
        const val MAX = 517L
    }
}

data class DeviceFilter(
    val name: String? = null,
    val address: String? = null,
    val serviceUuid: String? = null
) {
    fun filter(device: BluetoothDevice): Boolean =
        filterMacAddress(device) && filterName(device) && filterServiceUuid(device)

    private fun filterName(device: BluetoothDevice): Boolean =
        if (name == null) true
        else device.name == name

    private fun filterMacAddress(device: BluetoothDevice): Boolean =
        if (address == null) true
        else device.address == address

    //
    private fun filterServiceUuid(device: BluetoothDevice): Boolean =
        when {
            serviceUuid == null -> true
            device.uuids == null -> false
            else -> device.uuids.any {
                it.uuid.toString().lowercase(Locale.US) == serviceUuid.lowercase(Locale.US)
            }
        }
}

data class ScannerSetting(
    val settings: ScanSettings,
    val filters: List<DeviceFilter>,
    // filter out all devices that are connected with Beckon or all devices are in beckon store
    val useFilter: Boolean
)

@Serializable
data class Requirement(
    @Serializable(with = UuidSerializer::class)
    val uuid: UUID,
    @Serializable(with = UuidSerializer::class)
    val service: UUID,
    val property: Property
) // mandatory characteristic

@Serializable
data class Descriptor(
    val requirements: List<Requirement> = emptyList(),
    val subscribes: List<Characteristic> = emptyList(),
    val reads: List<Characteristic> = emptyList(),
    val actionsOnConnected: List<BleAction> = emptyList()
)


@Serializable
sealed class BleAction {
    data class RequestMTU(val mtu: Mtu) : BleAction()
    data class RequestMTUWithExpectation(val mtu: Mtu, val expectedMtu: Mtu) : BleAction()
    data class Read(val characteristic: Characteristic) : BleAction()
    data class Write(val data: Data, val characteristic: Characteristic) : BleAction()
    data class Subscribe(val characteristic: Characteristic) : BleAction()
}

data class ScanResult(
    internal val device: BluetoothDevice,
    val rssi: Int,
    val scanRecord: ScanRecord?
) {
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

@Serializable
data class Characteristic(
    @Serializable(with = UuidSerializer::class)
    val uuid: UUID,
    @Serializable(with = UuidSerializer::class)
    val service: UUID
) {
    fun toRequirement(property: Property): Requirement {
        return Requirement(uuid, service, property)
    }
}

data class SplitPackage(val uuid: UUID, val mtu: Int, val data: Data)
data class Change(val uuid: UUID, val data: Data)
typealias State = Map<UUID, Data>

operator fun State.plus(change: Change): State {
    return this + (change.uuid to change.data)
}

@Serializable
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
