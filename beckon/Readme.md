# Beckon

Bluetooth library which is easy and stable

## Prerequisites Libraries

- RxJava
- Kotlin
- [Nordic BLE library](https://github.com/NordicSemiconductor/Android-BLE-Library/)


## Functional requirement

### As a client user I want
- scan the list of devices
- connect to a device
    - automatically reconnect
    - read the state and forget about the device



## API design
### BeckonClient
 - States of devices
 - Connected devices
 - Add a device
 - Remove a device
 - AdapterState?
 - Scan for device

### BeckonDevice
- Write
- Read
- Connect
- ConnectionState
- Bond/not bonded

