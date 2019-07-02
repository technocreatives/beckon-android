package com.technocreatives.beckon.mock

import com.technocreatives.beckon.Change
import com.technocreatives.beckon.WritableDeviceMetadata
import com.technocreatives.beckon.DeviceMetadata
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class MockDeviceBuilder {

    var changesQueue = mutableListOf<ChangeWithDelay>()
    private lateinit var metadata: WritableDeviceMetadata
    private lateinit var discoveredDevice: DeviceMetadata
    private var scheduler: Scheduler? = null

    fun metadata(metadata: WritableDeviceMetadata) = apply { this.metadata = metadata }
    fun discoveredDevice(discoveredDevice: DeviceMetadata) = apply { this.discoveredDevice = discoveredDevice }
    fun scheduler(scheduler: Scheduler) = apply { this.scheduler = scheduler }

    fun enqueue(change: Change, delayInSecond: Long = 0) = apply { changesQueue.add(ChangeWithDelay(change, delayInSecond)) }
    fun enqueue(change: ChangeWithDelay) = apply { changesQueue.add(change) }

    fun build(): MockDevice {
        return MockDevice(metadata, discoveredDevice, changesStream().share())
    }

    private fun changesStream(): Observable<Change> {
        println("changesStream $changesQueue")
        return if (changesQueue.isEmpty()) Observable.never()
        else {
            val result = addAccumulator(changesQueue.toList())
            println("changes list after accumulate $result")
            Observable.merge(result.map { it.toObservable(scheduler) })
        }
    }
}

data class MockDeviceData(
    val metadata: WritableDeviceMetadata,
    val discoveredDevice: DeviceMetadata,
    val changesQueue: List<ChangeWithDelay>
)

data class ChangeWithDelay(val change: Change, val delayInSecond: Long) {
    fun toObservable(scheduler: Scheduler? = null): Observable<Change> {
        return if (delayInSecond == 0L) Observable.just(change)
        else Observable.defer { Observable.just(this.change) }.delay(delayInSecond, TimeUnit.SECONDS, scheduler
                ?: Schedulers.computation())
    }
}

fun addAccumulator(list: List<ChangeWithDelay>): List<ChangeWithDelay> {
    return list.fold(emptyList(), { result, change ->
        val sum = if (result.isEmpty()) change.delayInSecond
        else result.last().delayInSecond + change.delayInSecond
        result + change.copy(delayInSecond = sum)
    })
}
