package com.technocreatives.beckon.mesh.utils

import arrow.core.Either

inline fun <L, R> Either<L, R>.tap(fn: (R) -> Unit) = this.map { it.also { fn(it) } }
inline fun <L, R> Either<L, R>.tapLeft(fn: (L) -> Unit) = this.mapLeft { it.also { fn(it) } }
