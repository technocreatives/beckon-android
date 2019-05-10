package com.technocreatives.example.common.extension

import java.util.UUID

fun String.toUuid(): UUID = UUID.fromString(this)