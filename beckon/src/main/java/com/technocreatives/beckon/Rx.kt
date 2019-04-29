package com.technocreatives.beckon

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.internal.disposables.DisposableContainer

inline fun <reified U> Observable<*>.filterIs(): Observable<U> {
    return this.filter { it is U }.map { it as U }
}

inline fun <reified U, V> Observable<*>.filterIs(noinline selector: (U) -> V): Observable<V> {
    return this.filterIs<U>().map(selector)
}

fun <T> Observable<T>.onUI(): Observable<T> {
    return this
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
}

fun <T> Single<T>.onUI(): Single<T> {
    return this
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
}

fun Completable.onUI(): Completable {
    return this
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
}

fun Disposable.disposedBy(container: DisposableContainer) {
    container.add(this)
}
