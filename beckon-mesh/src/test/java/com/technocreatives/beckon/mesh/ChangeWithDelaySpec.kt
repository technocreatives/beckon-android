package com.technocreatives.beckon.mesh

import com.nhaarman.mockitokotlin2.mock
import com.technocreatives.beckon.Change
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.TestScheduler
import no.nordicsemi.android.ble.data.Data
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.TimeUnit

object ChangeWithDelaySpec : Spek({
    group("ChangeWithDelay toObservable") {
        val byte = ByteArray(4) { i -> i.toByte() }
        val change = Change(mock(), mock(), Data(byte))

        val testScheduler by memoized { TestScheduler() }
        lateinit var testObserver: TestObserver<Change>

        listOf<Long>(0, 1, 10, 20, 30).forEach { delay ->
            describe("A change with $delay seconds delay") {
                beforeEach {
                    val changeWithDelay = ChangeWithDelay(change, delay)
                    testObserver = changeWithDelay.toObservable(testScheduler).test()
                    testScheduler.advanceTimeBy(delay, TimeUnit.SECONDS)
                }

                it("should have correct delay") {
                    testObserver.assertSubscribed()
                        .assertValueCount(1)
                        .assertValue(change)
                }
            }
        }
    }
})
