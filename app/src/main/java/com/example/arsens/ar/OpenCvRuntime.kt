package com.example.arsens.ar

import org.opencv.android.OpenCVLoader
import org.opencv.core.Core

object OpenCvRuntime {
    @Volatile
    private var loaded = false

    @Volatile
    var lastError: String? = null
        private set

    fun ensureLoaded(): Boolean {
        if (loaded) return true
        synchronized(this) {
            if (loaded) return true

            val localLoaded = runCatching { OpenCVLoader.initLocal() }
                .onFailure { lastError = it.message }
                .getOrDefault(false)

            val systemLoaded = if (localLoaded) {
                true
            } else {
                runCatching {
                    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
                    true
                }.onFailure {
                    lastError = it.message
                }.getOrDefault(false)
            }

            loaded = systemLoaded
            if (loaded) lastError = null
            return loaded
        }
    }
}
