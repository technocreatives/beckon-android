package com.technocreatives.example.bond

import com.technocreatives.beckon.CharacteristicMapper
import io.reactivex.functions.BiFunction

sealed class StateChange {
    class BondValue(val value: String) : StateChange()
    object UFO : StateChange()
}

data class DeviceState(val bondValue: String)

val mapper: CharacteristicMapper<StateChange> = {
    StateChange.BondValue("it works")
}

val deviceReducer = BiFunction<DeviceState, StateChange, DeviceState> { state, changes ->
    when(changes){
        is StateChange.BondValue -> DeviceState(changes.value)
        is StateChange.UFO -> state
    }
}