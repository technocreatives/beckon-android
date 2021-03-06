# Beckon [![](https://jitpack.io/v/technocreatives/beckon-android.svg)](https://jitpack.io/#technocreatives/beckon-android)

A safe, correct and pleasant Bluetooth Low Energy (BLE) library for Android with functional data types and RxJava interface.

## Introduction

This library is to solve all the pains that many Android developers have to endure in their life when working with Android BLE.

By using functional programming approach and Rxjava interface, this library (will) provides:

  - Sane and simple Api with Coroutines/Flow and [Rxjava](https://github.com/ReactiveX/RxJava).
  - The best error handling ever which is backed by [Arrow](https://arrow-kt.io/).
  - Support all BLE operations (read, write, notifications, bond). Thanks to [NordicSemiconductor/Android-BLE-Library](https://github.com/NordicSemiconductor/Android-BLE-Library)!

## Setup <a name = "setup"></a>

You can get `Beckon` by using [Jitpack](https://jitpack.io/#technocreatives/beckon).

```Gradle
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }

    implementation "com.github.technocreatives.beckon-android:beckon:$beckon"
    implementation "com.github.technocreatives.beckon-android:beckon-rx2:$beckon"
```

## Compatibility <a name = "compatibility"></a>

Supports RxJava2 and Arrow version `0.13.2`.

## Contributing <a name = "contributing"></a>

Any bug reports, feature requests, questions and pull requests are very welcome.



## Feature

- Scan
- Connect/disconnect a device
- Create/remove Bond
- Save/remove (A new concept from Beckon)

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

TBD (too much to write them all)

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
