---
title: Beckon
subTitle: Beckon
date: 2019/04/11
tags: ["android", "library", "bluetooth", "ttc"]
---

# Beckon

A Bluetooth library which should be easy and stable.

## Case study

### Axkid

1. Scan
  - Scan devices with filters
  - Connect to all scanned devices
  - Read/Notify state of all devices
  - Saved a device with correct state then disconnect other connected devices

2. Use
  - Recive state of the saved device by notify
  - Try to reconnect whenever the devices is disconnected.
  - Should notify user when user remove bond manually.

3. Non functional requirements
  - Work in background

### Inkphat

1. Scan
  - Scan devices with filters
  - Connect to all scanned devices

2. Use
  - Rea/Write state to all devices

3. Non functional requirements
  - Work in background

## Requirement

- Limit bluetooth knowledge with client deveper
- Auto reconnect <Support Bondable devices only>
  - Device restart
  - Turn off bluetooth
  - Device is too far away
- Save connected devices

- Turn on/off Log

## Todo

- user removes bond device in system setting
- InvalidDataException for mapper?
- Use unit test to make sure logic works correctly.
- Teardown Models file
- How does Bond state work?
- reconnect all saved devices after death
- Use Simple Redux
- Update Mock module
- Use BeckonError class instead of Exception
- Add disconnect all/allSaved devices api for BeckonClient
- Support Dokka

## Doing

- Use state machine to manage connection state of a beckon device 50%


## Done
- Support Read on Characteristic
- Use Maybe, Single, Compeletable to make sense the api.
- Make findCharacteristic code shorter
- Solve connect and disconnect devices when scanning => scan connect and save in one screen
- Seperation of connected and saved devices
