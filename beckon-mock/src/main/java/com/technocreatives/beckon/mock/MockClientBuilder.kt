package com.technocreatives.beckon.mock

class MockClientBuilder {
    private var devices = mutableListOf<MockDevice>()

    fun addDevice(device: MockDevice) = apply { this.devices.add(device) }
    fun addDevices(devices: List<MockDevice>) = apply { this.devices.addAll(devices) }

    fun build(): MockBeckonClient {
        return MockBeckonClient(devices)
    }
}
