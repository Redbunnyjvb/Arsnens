package com.example.arsens.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The old complete file survives an interrupted write. Android minSdk supports NIO. */
internal fun atomicWrite(file: File, content: String) {
    file.parentFile?.mkdirs()
    val temporary = File.createTempFile(file.name, ".pending", file.parentFile)
    try {
        FileOutputStream(temporary).use { stream -> stream.write(content.toByteArray(Charsets.UTF_8)); stream.fd.sync() }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { temporary.delete() }
}
