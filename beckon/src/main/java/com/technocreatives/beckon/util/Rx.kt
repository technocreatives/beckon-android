package com.technocreatives.beckon.util

import io.reactivex.CompletableEmitter
import io.reactivex.disposables.Disposable
import io.reactivex.internal.disposables.DisposableContainer

fun Disposable.disposedBy(container: DisposableContainer) {
    container.add(this)
}

fun CompletableEmitter.safe(f: (CompletableEmitter.() -> Unit)) {
    if (!this.isDisposed) {
        f.invoke(this)
    }
}
