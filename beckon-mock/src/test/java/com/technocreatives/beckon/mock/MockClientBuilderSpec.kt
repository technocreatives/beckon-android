package com.technocreatives.beckon.mock

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.technocreatives.beckon.DeviceMetadata
import io.reactivex.observers.TestObserver
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object MockClientBuilderSpec : Spek({
    describe("build") {
        val metadata = DeviceMetadata("macAddress", "axkid", emptyList())
        val device = mock<MockDevice>()

        val builder by memoized { MockClientBuilder() }

        lateinit var testObserver: TestObserver<List<DeviceMetadata>>

        beforeEach {
            whenever(device.metadata()).thenReturn(metadata)
            testObserver = builder.addDevice(device)
                    .build().devices().test()
        }

        it("should return correct size") {
            testObserver.assertSubscribed()
                    .assertValueCount(1)
                    .assertNotComplete()
                    .assertValueAt(0) {
                        it.size == 1
                    }
                    .assertValueAt(0) {
                        it[0] == metadata
                    }
        }
    }
})
