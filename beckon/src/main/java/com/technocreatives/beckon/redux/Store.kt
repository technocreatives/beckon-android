package com.technocreatives.beckon.redux

import io.reactivex.Observable

internal interface Store : Dispatcher {
    fun states(): Observable<BeckonState>

    fun currentState(): BeckonState
}

internal interface Dispatcher {
    fun dispatch(action: Action)
}