package com.technocreatives.beckon.mesh

import android.bluetooth.BluetoothGattCharacteristic
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.nhaarman.mockitokotlin2.mock
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicResult
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.Type
import com.technocreatives.beckon.WritableDeviceMetadata
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.TestScheduler
import no.nordicsemi.android.ble.data.Data
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

fun String.toUuid(): UUID = UUID.fromString(this)

object MockDeviceBuilderSpec : Spek({

    describe("changes stream 2") {
        val serviceUUID = "0000fff0-0000-1000-8000-00805f9b34fb"
        val seatUuId = "0000fff3-0000-1000-8000-00805f9b34fb"
        val temperatureUuid = "0000fff2-0000-1000-8000-00805f9b34fb"
        val randomUUID = "1111fff3-0000-1000-8000-00805f9b34fb"

        val characteristics = listOf(
            Characteristic(temperatureUuid.toUuid(), serviceUUID.toUuid(), listOf(Type.NOTIFY), required = true),
            Characteristic(seatUuId.toUuid(), serviceUUID.toUuid(), listOf(Type.NOTIFY), required = true),
            Characteristic(randomUUID.toUuid(), serviceUUID.toUuid(), listOf(Type.NOTIFY), required = false)
        )

        val metadata = WritableDeviceMetadata("macaddress", "axkid", characteristics)
        val characteristicResults =
            characteristics.map { CharacteristicResult.Success(it, BluetoothGattCharacteristic(it.uuid, 0, 0)) }
        val discoveredDevice = DeviceMetadata(metadata.macAddress, metadata.name, characteristicResults)
        val c1 = byteArrayOf(1.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        val c2 = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())

        Timber.d("Byte ${Data(c1).getIntValue(Data.FORMAT_SINT8, 0)}")
        val testScheduler by memoized { TestScheduler() }

        val mockDevice by memoized {
            MockDeviceBuilder()
                .metadata(metadata)
                .discoveredDevice(discoveredDevice)
                .enqueue(Change(metadata, characteristics[1], Data(c2)), 0L)
                .enqueue(Change(metadata, characteristics[1], Data(c1)), 0L)
                .enqueue(Change(metadata, characteristics[1], Data(c2)), 30)
                .enqueue(Change(metadata, characteristics[1], Data(c1)), 30)
                .enqueue(Change(metadata, characteristics[1], Data(c2)), 30)
                .enqueue(Change(metadata, characteristics[1], Data(c1)), 30)
                .scheduler(testScheduler)
                .build()
        }

        lateinit var testObserver: TestObserver<Change>

        beforeEach {
            testObserver = mockDevice.changes().test()
        }

        describe("enqueue  changes with delays") {

            it("should emit first change immediately") {
                testObserver
                    .assertSubscribed()
                    .assertValueCount(2)
            }

            describe("after 30 seconds") {

                beforeEach { testScheduler.advanceTimeBy(30, TimeUnit.SECONDS) }

                it("should emit 1 change") {
                    testObserver
                        .assertSubscribed()
                        .assertValueCount(3)
                }
            }

            describe("after 60 seconds") {

                beforeEach { testScheduler.advanceTimeBy(60, TimeUnit.SECONDS) }

                it("should emit 2 changes") {
                    testObserver
                        .assertSubscribed()
                        .assertValueCount(4)
                }
            }
        }
    }

    describe("enqueue") {
        val byte = ByteArray(4) { i -> i.toByte() }
        val change = Change(mock(), mock(), Data(byte))

        val metadata = mock<WritableDeviceMetadata>()
        val discoveredDevice = mock<DeviceMetadata>()

        val mockDeviceBuilder by memoized { MockDeviceBuilder() }

        val testScheduler by memoized { TestScheduler() }

        lateinit var builder: MockDeviceBuilder
        describe("add 5 changes to builder") {

            beforeEach {
                builder = mockDeviceBuilder.metadata(metadata)
                    .discoveredDevice(discoveredDevice)
                    .enqueue(change, 30)
                    .enqueue(change, 30)
                    .enqueue(change, 30)
                    .enqueue(change, 30)
                    .enqueue(change, 30)
                    .scheduler(testScheduler)
            }

            it("changesQueue should have 5 items") {
                assertThat(builder.changesQueue.size).isEqualTo(5)
            }
        }
    }

    describe("changes stream") {

        val byte = ByteArray(4) { i -> i.toByte() }
        val change = Change(mock(), mock(), Data(byte))

        val delays = listOf<Long>(0, 1, 2, 4, 8)
        val changes = delays.map {
            ChangeWithDelay(change, it)
        }

        val metadata = mock<WritableDeviceMetadata>()
        val discoveredDevice = mock<DeviceMetadata>()

        val mockDeviceBuilder by memoized { MockDeviceBuilder() }

        val testScheduler by memoized { TestScheduler() }
        lateinit var testObserver: TestObserver<Change>

        describe("enqueue ${delays.size} changes with delays $delays") {

            beforeEach {
                val builder = mockDeviceBuilder.metadata(metadata)
                    .discoveredDevice(discoveredDevice)
                    .scheduler(testScheduler)

                changes.forEach {
                    println("Change $it")
                    builder.enqueue(it)
                }

                testObserver = builder.build().changes().test()
            }

            it("should emit first change immediately") {
                testObserver
                    .assertSubscribed()
                    .assertValueCount(1)
            }

            describe("after 1 second") {

                beforeEach { testScheduler.advanceTimeBy(1, TimeUnit.SECONDS) }

                it("should emit 2 changes") {
                    testObserver
                        .assertSubscribed()
                        .assertValueCount(2)
                        .assertValueSet(listOf(change))
                }
            }

            describe("after 3 seconds") {

                beforeEach { testScheduler.advanceTimeBy(3, TimeUnit.SECONDS) }

                it("should emit 3 changes") {
                    testObserver
                        .assertSubscribed()
                        .assertValueCount(3)
                        .assertValueSet(listOf(change))
                }
            }

            describe("after 7 seconds") {

                beforeEach { testScheduler.advanceTimeBy(7, TimeUnit.SECONDS) }

                it("should emit 4 changes") {
                    testObserver
                        .assertSubscribed()
                        .assertValueCount(4)
                        .assertValueSet(listOf(change))
                }
            }

            describe("after 10 seconds") {

                beforeEach { testScheduler.advanceTimeBy(10, TimeUnit.SECONDS) }

                it("should emit 4 changes") {
                    testObserver
                        .assertSubscribed()
                        .assertValueCount(4)
                        .assertValueSet(listOf(change))
                }
            }

            describe("after 15 seconds") {

                beforeEach { testScheduler.advanceTimeBy(15, TimeUnit.SECONDS) }

                it("should emit 5 changes") {
                    testObserver
                        .assertSubscribed()
                        .assertValueCount(5)
                        .assertValueSet(listOf(change))
                }
            }
        }
    }

    describe("addAccumulator") {
        lateinit var result: List<ChangeWithDelay>
        val byte = ByteArray(4) { i -> i.toByte() }
        val change = Change(mock(), mock(), Data(byte))

        val delays = listOf<Long>(0, 1, 2, 4, 8)
        val changes = delays.map {
            ChangeWithDelay(change, it)
        }
        beforeEach {
            result = addAccumulator(changes)
        }

        it("1st item") {
            assertThat(0L).isEqualTo(result[0].delayInSecond)
        }

        it("2nd item") {
            assertThat(1L).isEqualTo(result[1].delayInSecond)
        }

        it("3rd item") {
            assertThat(3L).isEqualTo(result[2].delayInSecond)
        }

        it("4th item") {
            assertThat(7L).isEqualTo(result[3].delayInSecond)
        }

        it("5th item") {
            assertThat(15L).isEqualTo(result[4].delayInSecond)
        }
    }

    describe("addAccumulator aaa") {
        lateinit var result: List<ChangeWithDelay>
        val byte = ByteArray(4) { i -> i.toByte() }
        val change = Change(mock(), mock(), Data(byte))

        val delays = listOf<Long>(1, 1, 2, 4, 8)
        val changes = delays.map {
            ChangeWithDelay(change, it)
        }

        beforeEach {
            result = addAccumulator(changes)
            println(result)
        }

        it("1st item") {
            assertThat(1L).isEqualTo(result[0].delayInSecond)
        }

        it("2nd item") {
            assertThat(2L).isEqualTo(result[1].delayInSecond)
        }

        it("3rd item") {
            assert(result[2].delayInSecond == 4L)
            assertThat(4L).isEqualTo(result[2].delayInSecond)
        }

        it("4th item") {
            assert(result[3].delayInSecond == 8L)
            assertThat(8L).isEqualTo(result[3].delayInSecond)
        }

        it("5th item") {
            assertThat(16L).isEqualTo(result[4].delayInSecond)
        }
    }
})
