package com.technocreatives.beckon

import io.reactivex.Observable

fun <E> E.justever(): Observable<E> {
    return Observable.create {
        it.onNext(this)
    }
}
