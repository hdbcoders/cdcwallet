package com.hdbcoders.cdcwallet.data.db

/**
 * sqlcipher-android does not self-load its native library; it must be loaded
 * explicitly before the first database open (once per process).
 */
object SqlCipherNative {
    @Volatile
    private var loaded = false

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                System.loadLibrary("sqlcipher")
                loaded = true
            }
        }
    }
}
