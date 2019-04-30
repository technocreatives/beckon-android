package com.technocreatives.beckon.redux

internal sealed class Action

internal class AddDevice(val device: Device) : Action()
internal class RemoveDevice(val device: Device) : Action()
