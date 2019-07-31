package com.technocreatives.beckon.util

import arrow.core.Either
import arrow.core.right
import com.technocreatives.beckon.BeckonResult
import io.reactivex.Observable
import io.reactivex.Single
import java.util.Locale.filter

fun <T, R> Observable<Either<Throwable, T>>.flatMapEither(mapper: (T) -> Observable<R>): Observable<Either<Throwable, R>> {
    return flatMap {
        when (it) {
            is Either.Right -> mapper(it.b).map { it.right() as BeckonResult<R> }
            is Either.Left -> Observable.just(it)
        }
    }
}

fun <T, R> Observable<Either<Throwable, T>>.concatMapEither(mapper: (T) -> Observable<R>): Observable<Either<Throwable, R>> {
    return concatMap {
        when (it) {
            is Either.Right -> mapper(it.b).map { it.right() as BeckonResult<R> }
            is Either.Left -> Observable.just(it)
        }
    }
}

fun <T, R> Observable<Either<Throwable, T>>.switchMapEither(mapper: (T) -> Observable<R>): Observable<Either<Throwable, R>> {
    return flatMap {
        when (it) {
            is Either.Right -> mapper(it.b).map { it.right() as BeckonResult<R> }
            is Either.Left -> Observable.just(it)
        }
    }
}

fun <T, R> Observable<Either<Throwable, T>>.flatMapSingleEither(mapper: (T) -> Single<R>): Observable<Either<Throwable, R>> {
    return flatMapSingle {
        when (it) {
            is Either.Right -> mapper(it.b).map { it.right() as BeckonResult<R> }
            is Either.Left -> Single.just(it)
        }
    }
}

fun <T, R> Observable<Either<Throwable, T>>.concatMapSingleEither(mapper: (T) -> Single<R>): Observable<Either<Throwable, R>> {
    return concatMapSingle {
        when (it) {
            is Either.Right -> mapper(it.b).map { it.right() as BeckonResult<R> }
            is Either.Left -> Single.just(it)
        }
    }
}

fun <T> Observable<Either<Throwable, T>>.filterEither(filter: (T) -> Boolean): Observable<Either<Throwable, T>> {
    return filter {
        when (it) {
            is Either.Right -> filter(it.b)
            is Either.Left -> true
        }
    }
}
