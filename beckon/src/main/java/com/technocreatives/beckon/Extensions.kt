package com.technocreatives.beckon

import io.reactivex.Observable
import io.reactivex.functions.BiFunction

fun <Change, State> BeckonDevice.states(
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<State> {
    return this.changes()
            .map { mapper(it) }
            .scan(defaultState, reducer)
}
