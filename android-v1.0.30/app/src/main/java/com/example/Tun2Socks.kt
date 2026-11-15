package com.example

import android.os.ParcelFileDescriptor

/**
 * hev-socks5-tunnel (app/hev-socks5-tunnel), the C library that takes the VPN's
 * packets apart and opens a SOCKS5 connection to the Arman Go core for each TCP
 * connection and UDP flow, on a thread of its own.
 *
 * The library binds these native methods to this class by name when it loads:
 * proguard-rules.pro keeps them, and app/build.gradle.kts names the class.
 */
object Tun2Socks {
    private external fun TProxyStartService(configPath: String, fd: Int): Boolean
    private external fun TProxyStopService(): Boolean
    private external fun TProxyIsRunning(): Boolean
    private external fun TProxyInstallLogFilter(): Boolean

    @Suppress("unused")
    private external fun TProxyGetStats(): LongArray

    private var device: ParcelFileDescriptor? = null
    private val logFilterLock = Any()

    @Volatile
    private var logFilterInstalled = false

    fun installLogFilter() {
        if (logFilterInstalled) return
        synchronized(logFilterLock) {
            if (logFilterInstalled) return
            runCatching {
                System.loadLibrary("hev-socks5-tunnel")
                logFilterInstalled = TProxyInstallLogFilter()
            }
        }
    }

    @Synchronized
    fun start(configPath: String, tun: ParcelFileDescriptor): Boolean {
        try {
            stop()
            System.loadLibrary("hev-socks5-tunnel")
            installLogFilter()
            device = tun
            if (TProxyStartService(configPath, tun.fd)) return true
        } catch (e: Throwable) {
            device = null
            tun.close()
            throw e
        }
        stop()
        return false
    }

    @Synchronized
    fun stop() {
        val tun = device ?: return
        device = null
        TProxyStopService()
        tun.close()
    }

    @Synchronized
    fun ended(): Boolean = device != null && !TProxyIsRunning()
}
