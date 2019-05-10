package com.technocreatives.example.common.extension

import io.reactivex.disposables.Disposable
import io.reactivex.internal.disposables.DisposableContainer


fun Disposable.disposedBy(container: DisposableContainer) {
    container.add(this)
}