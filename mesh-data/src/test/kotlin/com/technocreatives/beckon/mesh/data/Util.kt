package com.technocreatives.beckon.mesh.data

import org.apache.commons.io.IOUtils
import java.io.IOException
import java.nio.charset.Charset

@Throws(IOException::class)
fun Class<*>.stringFrom(filePath: String): String =
    IOUtils.toString(getResourceAsStream(filePath), Charset.defaultCharset())
