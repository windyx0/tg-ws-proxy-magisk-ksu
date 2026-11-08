package com.windyx0.tgbypass.proxy

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.json.JSONObject

interface ProxyLibrary : Library {
    companion object {
        val INSTANCE: ProxyLibrary by lazy {
            Native.load("tgwsproxy", ProxyLibrary::class.java) as ProxyLibrary
        }
    }

    fun StartProxy(host: String, port: Int, dcIps: String, verbose: Int): Int
    fun StopProxy(): Int
    fun SetPoolSize(size: Int)
    fun GetStats(): Pointer?
    fun ResetCFDomainCooldowns()
    fun SetManualCFDomains(domains: String)
    fun SetCachedCFDomains(domains: String)
    fun GetAdaptiveRouteStats(): Pointer?
    fun GetProxyStatus(): Pointer?
    fun StartMtProtoProxy(host: String, port: Int, secret: String, runtimeConfig: String, verbose: Int): Int
    fun StopMtProtoProxy(): Int
    fun UpdateMtProtoRoutePolicy(runtimeConfig: String): Int
    fun GetMtProtoProxyStatus(): Pointer?
    fun FreeString(p: Pointer)
}

object NativeProxy {
    fun startMtProtoProxy(
        host: String,
        port: Int,
        secret: String,
        runtimeConfig: String,
        verbose: Int = 0,
    ): Int {
        return try {
            ProxyLibrary.INSTANCE.StartMtProtoProxy(host, port, secret, runtimeConfig, verbose)
        } catch (e: Throwable) {
            -1
        }
    }

    fun stopMtProtoProxy(): Int {
        return try {
            ProxyLibrary.INSTANCE.StopMtProtoProxy()
        } catch (e: Throwable) {
            -1
        }
    }

    fun updateMtProtoRoutePolicy(runtimeConfig: String): Int {
        return try {
            ProxyLibrary.INSTANCE.UpdateMtProtoRoutePolicy(runtimeConfig)
        } catch (e: Throwable) {
            -1
        }
    }

    fun getMtProtoProxyStatus(): String? {
        return try {
            val ptr = ProxyLibrary.INSTANCE.GetMtProtoProxyStatus() ?: return null
            val res = ptr.getString(0)
            ProxyLibrary.INSTANCE.FreeString(ptr)
            res
        } catch (e: Throwable) {
            null
        }
    }

    fun setCachedCfDomains(domains: List<String>) {
        try {
            val payload = domains.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString("|")
            ProxyLibrary.INSTANCE.SetCachedCFDomains(payload)
        } catch (_: Throwable) {}
    }

    fun resetCfDomainCooldowns() {
        try {
            ProxyLibrary.INSTANCE.ResetCFDomainCooldowns()
        } catch (_: Throwable) {}
    }
}
