package com.technocreatives.beckon.util

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.BeckonException
import com.technocreatives.beckon.GeneralError
import io.reactivex.Observable
import io.reactivex.Single

fun <E, T> Single<Either<E, T>>.fix(): Single<T>
where E : BeckonError {
    return flatMap {
        when (it) {
            is Either.Right -> Single.just(it.b)
            is Either.Left -> Single.error(BeckonException(it.a))
        }
    }
}

fun <E, T> Observable<Either<E, T>>.fix(): Observable<T>
    where E : BeckonError {
    return flatMap {
        when (it) {
            is Either.Right -> Observable.just(it.b)
            is Either.Left -> Observable.error(BeckonException(it.a))
        }
    }
}

fun <T> Single<T>.either(): Single<Either<BeckonError, T>> {
    return this.map { it.right() as Either<BeckonError, T> }
        .onErrorReturn {
            if (it is BeckonException) {
                it.beckonError.left()
            } else {
                GeneralError(it).left()
            }
        }
}

fun <T> Observable<T>.either(): Observable<Either<BeckonError, T>> {
    return map { it.right() as Either<BeckonError, T> }
        .onErrorReturn {
            if (it is BeckonException) {
                it.beckonError.left()
            } else {
                GeneralError(it).left()
            }
        }
}
