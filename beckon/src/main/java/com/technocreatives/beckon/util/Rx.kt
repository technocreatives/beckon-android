package com.technocreatives.beckon.util

import io.reactivex.CompletableEmitter
import io.reactivex.SingleEmitter
import io.reactivex.disposables.Disposable
import io.reactivex.internal.disposables.DisposableContainer

fun Disposable.disposedBy(container: DisposableContainer) {
    container.add(this)
}

fun CompletableEmitter.safe(lamda: (CompletableEmitter.() -> Unit)) {
    if (!this.isDisposed) {
        lamda.invoke(this)
    }
}

fun <T> SingleEmitter<T>.safe(lamda: (SingleEmitter<T>.() -> Unit)) {
    if (!this.isDisposed) {
        lamda.invoke(this)
    }
}
