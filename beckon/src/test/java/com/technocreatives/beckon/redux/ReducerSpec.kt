package com.technocreatives.beckon.redux

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.doesNotContain
import strikt.assertions.hasSize
import strikt.assertions.isEmpty

object ReducerSpec : Spek({
    lateinit var store: BeckonStore

    val macAddress = "macAddress"
    val connectedAddress1 = "macAddress1"
    val connectedAddress2 = "macAddress2"
    val connectingAddress1 = "macAddress3"
    val connectingAddress2 = "macAddress4"
    val connectingAddress3 = "macAddress5"

    val connectedDevices = listOf(connectedAddress1, connectedAddress2).map { beckonDevice(it) }
    val connectingDevices =
        listOf(connectingAddress1, connectingAddress2, connectingAddress3).map { savedMetadata(it) }

    group("Given a Store with the initial state") {

        beforeEachTest {
            store = testBeckonStore(initialState)
        }

        describe("when dispatch AddConnectedDevice action") {
            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectedDevice(beckonDevice(macAddress)))
            }

            it("then connected devices in current state should have size 1") {
                expectThat(store.currentState().connectedDevices).hasSize(1)
            }

            it("then connecting devices should be empty") {
                expectThat(store.currentState().connectingDevices).isEmpty()
            }

            it("then connected devices in current state should contain the connected device") {
                expectThat(store.currentState().connectedMacAddresses())
                    .contains(macAddress)
            }
        }

        describe("when dispatch RemoveConnectedDevice action") {
            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectedDevice(beckonDevice(macAddress)))
            }

            it("then connected devices in current state should be empty") {
                expectThat(store.currentState().connectedDevices).isEmpty()
            }

            it("then connecting devices should be empty") {
                expectThat(store.currentState().connectingDevices).isEmpty()
            }
        }

        describe("when dispatch AddConnectingDevice action") {
            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectingDevice(savedMetadata(macAddress)))
            }

            it("then connecting devices in current state should have size 1") {
                expectThat(store.currentState().connectingDevices).hasSize(1)
            }

            it("then connecting devices in current state should contain the connecting device") {
                expectThat(store.currentState().connectingMacAddresses())
                    .contains(macAddress)
            }

            it("then connected devices in current state should be empty") {
                expectThat(store.currentState().connectedDevices).isEmpty()
            }
        }

        describe("when dispatch RemoveConnectingDevice action") {
            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectingDevice(savedMetadata(macAddress)))
            }

            it("then connected devices in current state should be empty") {
                expectThat(store.currentState().connectedDevices).isEmpty()
            }

            it("then connecting devices should be empty") {
                expectThat(store.currentState().connectingDevices).isEmpty()
            }
        }

        describe("when dispatch RemoveAllConnectedDevices action") {
            beforeEachTest {
                store.dispatch(BeckonAction.RemoveAllConnectedDevices)
            }

            it("then connected devices in current state should have size 0") {
                expectThat(store.currentState().connectedDevices).hasSize(0)
            }
        }
    }

    group("Given a Store with  ${connectedDevices.size} connected devices and 0 connecting devices") {

        beforeEachTest {
            store = testBeckonStore(initialState.copy(connectedDevices = connectedDevices))
        }

        describe("when dispatch AddConnectedDevice with a nonexistent connected Device") {

            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectedDevice(beckonDevice(macAddress)))
            }

            it("then connected devices in current state should have size ${connectedDevices.size + 1}") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size + 1)
            }

            it("then connected devices in current state should contain the connected device") {
                expectThat(store.currentState().connectedMacAddresses())
                    .contains(macAddress)
            }
        }

        describe("when dispatch AddConnectedDevice with an existing connected Device") {

            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectedDevice(beckonDevice(connectedAddress1)))
            }

            it("then connected devices in current state should have size ${connectedDevices.size}") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size)
            }

            it("then connected devices in current state should contain the connected device") {
                expectThat(store.currentState().connectedMacAddresses())
                    .contains(connectedAddress1)
            }
        }

        describe("when dispatch RemoveConnectedDevice with a nonexistent connected Device") {

            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectedDevice(beckonDevice(macAddress)))
            }

            it("then connected devices in current state should have the same size") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size)
            }
        }

        describe("when dispatch RemoveConnectedDevice with an existing connected Device") {

            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectedDevice(beckonDevice(connectedAddress1)))
            }

            it("then connected devices in current state should have size ${connectedDevices.size - 1}") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size - 1)
            }

            it("then connected devices in current state should not contain the removed device") {
                expectThat(store.currentState().connectedMacAddresses())
                    .doesNotContain(connectedAddress1)
            }
        }

        describe("when dispatch AddConnectingDevice action") {
            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectingDevice(savedMetadata(macAddress)))
            }

            it("then connecting devices in current state should have size 1") {
                expectThat(store.currentState().connectingDevices).hasSize(1)
            }

            it("then connecting devices in current state should contain the connecting device") {
                expectThat(store.currentState().connectingMacAddresses())
                    .contains(macAddress)
            }

            it("then connected devices in current state should have the same size") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size)
            }
        }

        describe("when dispatch RemoveConnectingDevice action") {
            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectingDevice(savedMetadata(macAddress)))
            }

            it("then connected devices in current state should have the same size") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size)
            }

            it("then connecting devices in current state should be empty") {
                expectThat(store.currentState().connectingDevices).isEmpty()
            }
        }

        describe("when dispatch RemoveAllConnectedDevices") {

            beforeEachTest {
                store.dispatch(BeckonAction.RemoveAllConnectedDevices)
            }

            it("then connected devices in current state should be empty") {
                expectThat(store.currentState().connectedDevices).isEmpty()
            }
        }
    }

    group("Given a Store with 0 connected devices and ${connectingDevices.size} connecting devices") {

        beforeEachTest {
            store = testBeckonStore(initialState.copy(connectingDevices = connectingDevices))
        }

        describe("when dispatch AddConnectedDevice action") {
            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectedDevice(beckonDevice(macAddress)))
            }

            it("then connected devices in current state should have size 1") {
                expectThat(store.currentState().connectedDevices).hasSize(1)
            }

            it("then connected devices in current state should contain the connecting device") {
                expectThat(store.currentState().connectedMacAddresses())
                    .contains(macAddress)
            }

            it("then connecting devices in current state should have the same size") {
                expectThat(store.currentState().connectingDevices).hasSize(connectingDevices.size)
            }
        }

        describe("when dispatch RemoveConnectedDevice action") {
            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectedDevice(beckonDevice(macAddress)))
            }

            it("then connected devices in current state should be empty") {
                expectThat(store.currentState().connectedDevices).isEmpty()
            }

            it("then connecting devices in current state should have the same size") {
                expectThat(store.currentState().connectingDevices).hasSize(connectingDevices.size)
            }
        }
    }

    group("Given a Store with ${connectedDevices.size} connected devices and ${connectingDevices.size} connecting devices") {

        lateinit var currentState: BeckonState

        beforeEachTest {
            store = testBeckonStore(
                initialState.copy(
                    connectedDevices = connectedDevices,
                    connectingDevices = connectingDevices
                )
            )
            currentState = store.currentState()
        }

        describe("when add a new connected device") {

            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectedDevice(beckonDevice(macAddress)))
            }

            it("then connected devices should have size ${connectedDevices.size + 1}") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size + 1)
            }

            it("then connected devices should contain the new connected device") {
                expectThat(store.currentState().connectedMacAddresses())
                    .contains(macAddress)
            }

            it("then connecting devices should have the same size ${connectingDevices.size}") {
                expectThat(store.currentState().connectingDevices).hasSize(connectingDevices.size)
            }
        }

        describe("when add an existing connected device") {

            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectedDevice(beckonDevice(connectedAddress1)))
            }

            it("then the state should not change") {
                expectThat(store.currentState()).isNotChanged(currentState)
            }
        }

        describe("when add a connected device from connecting devices") {

            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectedDevice(beckonDevice(connectingAddress1)))
            }

            it("then connected devices should have size ${connectedDevices.size + 1}") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size + 1)
            }

            it("then connected devices should contain the connected device") {
                expectThat(store.currentState())
                    .hasConnectedDevice(connectingAddress1)
            }

            it("then connecting devices state should have size ${connectedDevices.size - 1}") {
                expectThat(store.currentState().connectingDevices).hasSize(connectingDevices.size - 1)
            }

            it("then connecting devices should not contain the connected device") {
                expectThat(store.currentState())
                    .doesNotHaveConnectingDevice(connectingAddress1)
            }
        }

        describe("when remove an existing connected device") {

            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectedDevice(beckonDevice(connectedAddress1)))
            }

            it("then connected devices should have size ${connectedDevices.size - 1}") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size - 1)
            }

            it("then connected devices in current state should not contain the removed device") {
                expectThat(store.currentState())
                    .doesNotHaveConnectedDevice(connectedAddress1)
            }

            it("then connecting devices should have the same size ${connectingDevices.size}") {
                expectThat(store.currentState().connectingDevices).hasSize(connectingDevices.size)
            }
        }

        describe("when remove a nonexistent connected device") {

            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectedDevice(beckonDevice(macAddress)))
            }

            it("then the state should not change") {
                expectThat(store.currentState()).isNotChanged(currentState)
            }
        }

        describe("when add a new connecting device") {
            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectingDevice(savedMetadata(macAddress)))
            }

            it("then connecting devices should have size ${connectingDevices.size + 1}") {
                expectThat(store.currentState().connectingDevices).hasSize(connectingDevices.size + 1)
            }

            it("then connecting devices should contain the new connecting device") {
                expectThat(store.currentState())
                    .hasConnectingDevice(macAddress)
            }

            it("then connected devices should have the same size ${connectedDevices.size}") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size)
            }
        }

        describe("when add an existing connecting device") {

            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectingDevice(savedMetadata(connectingAddress1)))
            }

            it("then the state should not change") {
                expectThat(store.currentState()).isNotChanged(currentState)
            }
        }

        describe("when add a connecting device from connected devices") {
            beforeEachTest {
                store.dispatch(BeckonAction.AddConnectingDevice(savedMetadata(connectedAddress1)))
            }

            it("then connected devices state should have size ${connectedDevices.size - 1}") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size - 1)
            }

            it("then connected devices should not contain the connecting device") {
                expectThat(store.currentState())
                    .doesNotHaveConnectedDevice(connectedAddress1)
            }

            it("then connecting devices should have size ${connectingDevices.size + 1}") {
                expectThat(store.currentState().connectingDevices).hasSize(connectingDevices.size + 1)
            }

            it("then connecting devices should contain the connecting device") {
                expectThat(store.currentState())
                    .hasConnectingDevice(connectedAddress1)
            }
        }

        describe("when remove an existing connecting device") {

            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectingDevice(savedMetadata(connectingAddress1)))
            }

            it("then connected devices should have the same size ${connectedDevices.size}") {
                expectThat(store.currentState().connectedDevices).hasSize(connectedDevices.size)
            }

            it("then connecting devices should have size ${connectingDevices.size - 1}") {
                expectThat(store.currentState().connectingDevices).hasSize(connectingDevices.size - 1)
            }

            it("then connecting devices in current state should not contain the removed device") {
                expectThat(store.currentState())
                    .doesNotHaveConnectingDevice(connectingAddress1)
            }
        }

        describe("when remove a nonexistent connecting device") {

            beforeEachTest {
                store.dispatch(BeckonAction.RemoveConnectingDevice(savedMetadata(macAddress)))
            }

            it("then the state should not change") {
                expectThat(store.currentState()).isNotChanged(currentState)
            }
        }

        describe("when dispatch RemoveAllConnectedDevices") {

            beforeEachTest {
                store.dispatch(BeckonAction.RemoveAllConnectedDevices)
            }

            it("then connected devices in current state should be empty") {
                expectThat(store.currentState().connectedDevices).isEmpty()
            }
        }
    }
})
