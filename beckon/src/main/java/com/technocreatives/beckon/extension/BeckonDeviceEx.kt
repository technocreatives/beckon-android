package com.technocreatives.beckon.extension

import arrow.core.Either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicMapper
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.State
import com.technocreatives.beckon.checkNotifyList
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import java.util.UUID

data class BeckonState<State>(
    val metadata: Metadata,
    val connectionState: ConnectionState,
    val state: State
)

fun <Change> BeckonDevice.changes(characteristicUUID: UUID, mapper: CharacteristicMapper<Change>): Observable<Change> {
    return changes().filter { it.uuid == characteristicUUID }
        .map { mapper(it) }
}

fun <Change, State> BeckonDevice.states(
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<State> {
    return changes()
        .map { mapper(it) }
        .scan(defaultState, reducer)
}

fun BeckonDevice.deviceStates(): Observable<BeckonState<State>> {
    return Observable.combineLatest(
            states(),
            connectionStates(),
            BiFunction<State, ConnectionState, BeckonState<State>> { t1, t2 ->
                BeckonState(metadata(), t2, t1)
            })
}


fun <Change, State> BeckonDevice.deviceStates(
        mapper: CharacteristicMapper<Change>,
        reducer: (State, Change) -> State,
        defaultState: State
): Observable<BeckonState<State>> {
    return deviceStates(mapper, BiFunction { t1, t2 -> reducer(t1, t2) }, defaultState)
}

fun <Change, State> BeckonDevice.deviceStates(
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<BeckonState<State>> {
    val states = states(mapper, reducer, defaultState)
    return Observable.combineLatest(
        states,
        connectionStates(),
        BiFunction<State, ConnectionState, BeckonState<State>> { t1, t2 ->
            BeckonState(metadata(), t2, t1)
        })
}

fun <Change, State> BeckonDevice.subscribe(
    subscribes: List<Characteristic>,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<BeckonState<State>> {
    return subscribe(subscribes).andThen(deviceStates(mapper, reducer, defaultState))
}

fun BeckonDevice.subscribe(subscribes: List<Characteristic>): Completable {
    return when (val list = checkNotifyList(subscribes, metadata().services, metadata().characteristics)) {
        is Either.Left -> Completable.error(list.a.toException())
        is Either.Right -> subscribe(list.b)
    }
}

operator fun State.plus(change: Change): State {
    return this + (change.uuid to change.data)
}
