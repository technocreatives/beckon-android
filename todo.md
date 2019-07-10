---
title: Beckon
subTitle: Beckon
date: 2019/04/11
tags: ["android", "library", "bluetooth", "ttc"]
---

# Beckon

A Bluetooth library which should be easy and stable.

## Requirement

- Limit bluetooth knowledge with client deveper
- Auto reconnect <Support Bondable devices only>
  - Device restart
  - Turn off bluetooth
  - Device is too far away
- Save connected devices

- Turn on/off Log

## Todo

- InvalidDataException for mapper?
- Use unit test to make sure logic works correctly.
- Teardown Models file
- How does Bond state work?
- BleManagerCallbacks ????
- Seperation of connected and saved devices
- reconnect all saved devices after death
- Use Simple Redux
- Update Mock module
- Use state machine to manage connection state of a beckon device
- Use BeckonError class instead of Exception
- Add disconnect all/allSaved devices api for BeckonClient

## Doing

- Solve connect and disconnect devices when scanning => scan connect and save in one screen


## Done
- Support Read on Characteristic
- Use Maybe, Single, Compeletable to make sense the api.
- Make findCharacteristic code shorter
