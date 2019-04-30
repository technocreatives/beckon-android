package com.technocreatives.beckon.redux

import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber

internal class BeckonStore(private val reducer: Reducer, initialState: BeckonState) : Store {

    private val subject: BehaviorSubject<BeckonState> by lazy {
        BehaviorSubject.createDefault<BeckonState>(initialState)
    }

    override fun states(): Observable<BeckonState> {
        return subject.hide().distinctUntilChanged()
    }

    override fun dispatch(action: Action) {
        val newState = reducer(subject.value!!, action)
        Timber.d("Reducer Action: $action")
        Timber.d("Reducer NewState $newState")
        subject.onNext(newState)
    }

    override fun currentState(): BeckonState {
        return subject.value!!
    }
}