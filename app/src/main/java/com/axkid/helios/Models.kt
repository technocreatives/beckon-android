package com.axkid.helios

import com.axkid.helios.common.extension.toUuid
import com.technocreatives.beckon.BeckonDeviceFactory
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.Characteristics
import com.technocreatives.beckon.Reducer
import com.technocreatives.beckon.createFactory
import timber.log.Timber
import java.util.Date

sealed class Changes {
    class Seated(val isSeated: Boolean) : Changes()
    class Temperature(val value: Int) : Changes()
}

data class DeviceState(val seatedState: SeatedState, val temperature: Int, val created: Date)

sealed class SeatedState {
    object Unseated : SeatedState()
    class Seated(val time: Date) : SeatedState()
}

const val serviceUUID = "0000fff0-0000-1000-8000-00805f9b34fb"
const val seatUuId = "0000fff3-0000-1000-8000-00805f9b34fb"
const val temperatureUuid = "0000fff2-0000-1000-8000-00805f9b34fb"

val seatedCharacteristic: Characteristic<Changes> = Characteristic(seatUuId.toUuid()) {
    Timber.d("Seat data $it ${it.value} ${it.size()}")
    Changes.Seated(true)
}

val temperatureCharacteristic: Characteristic<Changes> = Characteristic(temperatureUuid.toUuid()) {
    Timber.d("Temperature data $it ${it.value} ${it.size()}")
    Changes.Temperature(30)
}

val characteristics = Characteristics(
    serviceUUID.toUuid(),
    listOf(seatedCharacteristic, temperatureCharacteristic)
)

val reducer: Reducer<Changes, DeviceState> = { changes, state ->
    when (changes) {
        is Changes.Seated -> {
            if (changes.isSeated) {
                state.copy(seatedState = SeatedState.Seated(time = Date()))
            } else {
                state.copy(seatedState = SeatedState.Unseated)
            }
        }
        is Changes.Temperature -> {
            state.copy(temperature = changes.value)
        }
    }
}

val factory: BeckonDeviceFactory<Changes, DeviceState> = createFactory(listOf(characteristics), reducer, DeviceState(SeatedState.Unseated, 22, Date()))
