# Beckon [![](https://jitpack.io/v/technocreatives/beckon-android.svg)](https://jitpack.io/#technocreatives/beckon-android)

A safe, correct and pleasant Bluetooth Low Energy (BLE) library for Android with functional data types and reactive interface.

## Introduction

This library aims to solve many of the pains that Android developers have to endure in their life when working with Android BLE.

By using functional programming approach and reactive interface, this library (will) provides:

  - Sane and simple Api with Coroutines/Flow.
  - The best error handling ever which is backed by [Arrow](https://arrow-kt.io/).
  - Support all BLE operations (read, write, notifications, bond). Thanks to [NordicSemiconductor/Android-BLE-Library](https://github.com/NordicSemiconductor/Android-BLE-Library)!
  - Support BLE Mesh. Thanks to [NordicSemiconductor/Android-nRF-Mesh-Library](https://github.com/NordicSemiconductor/Android-nRF-Mesh-Library)
  - Support DFU over BLE. Thanks to [NordicSemiconductor/Android-DFU-Library](https://github.com/NordicSemiconductor/Android-DFU-Library)

## Setup <a name = "setup"></a>

You can get `Beckon` by using [Jitpack](https://jitpack.io/#technocreatives/beckon-android).

```Gradle
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }

    // Beckon
    implementation "com.github.technocreatives.beckon-android:beckon:main-SNAPSHOT"

    // Beckon DFU
    implementation "com.github.technocreatives.beckon-android:beckon-dfu:main-SNAPSHOT"

    // Beckon Mesh
    implementation "com.github.technocreatives.beckon-android:mesh-data:main-SNAPSHOT"
    implementation "com.github.technocreatives.beckon-android:beckon-mesh:main-SNAPSHOT"
```

## Compatibility <a name = "compatibility"></a>

The library uses arrow, version `1.1.2`, to provide well typed [Either](https://arrow-kt.io/docs/apidocs/arrow-core/arrow.core/-either/) results for error handling. A basic understanding of how Arrow's work is essential.

## Contributing <a name = "contributing"></a>

Any bug reports, feature requests, questions and pull requests are very welcome.

## Modules & Features

### Beckon

Basic BLE connection to devices

- Scan
- Connect/disconnect a device
- Create/remove Bond
- Save/remove (A new concept from Beckon)
- Descriptor with requirements on a device

### Beckon DFU

Wrapper around [NordicSemiconductor/Android-DFU-Library](https://github.com/NordicSemiconductor/Android-DFU-Library)

- Sealed interface of results during the process of a DFU (Starting, EnablingBootloader, Uploading and Success/Aborted/Error)
- Process to view current state and abort a DFU process

### Beckon Mesh

- Exposes mesh states for Loaded, Connected & Provisioning
- Support multiple meshes
- Observe changes to mesh configuration
- Exposes sendMessage as suspend for Ack message
- Typed Configuration messages
- ProxyFilterMessage that types AccessPayload and OpCode (e.g Single, Double & Triple octet)

#### Mesh Data

- Well typed and formatted Mesh Config, compatible with [NordicSemiconductor/Android-nRF-Mesh-Library](https://github.com/NordicSemiconductor/Android-nRF-Mesh-Library) and [NordicSemiconductor/IOS-nRF-Mesh-Library](https://github.com/NordicSemiconductor/IOS-nRF-Mesh-Library)
- Serializable

## Workflow

```
-> Create a new branch
-> Push a lot of commits
-> Create a new PR to master
-> Push a lot of commits
-> Do code review & discuss
-> make sure ci agrees with what you did
-> Rebase to master (if needed)
-> Squash into one commit (and write a beautiful commit message)
-> If team agrees then merge to master
```

## Limitation

This library is not feature complete, feel free to make a PR if you wish to add functionality that is missing.

## License

Licensed under either of these:

- Apache License, Version 2.0, ([LICENSE-APACHE](LICENSE-APACHE) or
  https://www.apache.org/licenses/LICENSE-2.0)
- MIT license ([LICENSE-MIT](LICENSE-MIT) or
  https://opensource.org/licenses/MIT)

### Contributing

Unless you explicitly state otherwise, any contribution you intentionally submit
for inclusion in the work, as defined in the Apache-2.0 license, shall be
dual-licensed as above, without any additional terms or conditions.
