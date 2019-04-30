package com.technocreatives.beckon.redux

import io.reactivex.Observable

internal interface Store {
    fun states(): Observable<BeckonState>

    fun dispatch(action: Action)

    fun currentState(): BeckonState
}