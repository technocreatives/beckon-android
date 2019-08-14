package com.technocreatives.example

import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicMapper
import com.technocreatives.example.common.extension.toUuid
import io.reactivex.functions.BiFunction
import java.util.Date


const val serviceUUID = "0000fff0-0000-1000-8000-00805f9b34fb"
const val seatUuId = "0000fff3-0000-1000-8000-00805f9b34fb"
const val temperatureUuid = "0000fff2-0000-1000-8000-00805f9b34fb"
const val randomUUID = "1111fff3-0000-1000-8000-00805f9b34fb"

val characteristics =// emptyList<Characteristic>()
        listOf(
                Characteristic(seatUuId.toUuid(), serviceUUID.toUuid()),
                Characteristic(seatUuId.toUuid(), serviceUUID.toUuid()),
                Characteristic(randomUUID.toUuid(), serviceUUID.toUuid())
        )

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
    when {
        seatUuId.toUuid() == it.uuid -> AxkidChange.Seated(true)
        temperatureUuid.toUuid() == it.uuid -> AxkidChange.Temperature(30)
        else -> AxkidChange.UFO
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
