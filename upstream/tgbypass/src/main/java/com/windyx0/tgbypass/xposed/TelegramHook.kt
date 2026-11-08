package com.windyx0.tgbypass.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.lang.reflect.Method

class TelegramHook : IXposedHookLoadPackage {

    companion object {
        const val TAG = "TgBypassVector"
        const val PROXY_HOST = "127.0.0.1"
        const val PROXY_PORT = 1444
        const val PROXY_SECRET = "dd7ee8902211954d7e8235f108a6313d42"

        fun findMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? {
            return try {
                clazz.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            } catch (_: Throwable) {
                try {
                    clazz.getMethod(methodName, *parameterTypes)
                } catch (_: Throwable) {
                    null
                }
            }
        }

        val TARGET_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.messenger.beta",
            "org.thunderdog.challegram",
            "app.exteragram.messenger",
            "com.exteragram.messenger",
            "tw.nekomimi.nekogram",
            "cc.modery.cherrygram",
            "org.telegram.plus",
            "nekox.messenger",
            "org.telegram.BifToGram"
        )

        fun isTelegramIp(host: String): Boolean {
            return host.startsWith("149.154.") || host.startsWith("91.108.") || host.startsWith("185.76.151.")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in TARGET_PACKAGES) return

        XposedBridge.log("[$TAG] Successfully injected into Telegram client: ${lpparam.packageName}")

        try {
            hookTelegramSharedConfig(lpparam)
            hookConnectionsManager(lpparam)
            hookSocketConnect(lpparam)
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] General hook error: ${t.message}")
        }
    }

    private fun hookTelegramSharedConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        try {
            val sharedConfigClass = XposedHelpers.findClassIfExists("org.telegram.messenger.SharedConfig", classLoader)
                ?: return

            // 1. Force isProxyEnabled to true
            XposedHelpers.findAndHookMethod(
                sharedConfigClass,
                "isProxyEnabled",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )

            // 2. Inject local proxy after loadProxyList
            val loadProxyMethod = findMethod(sharedConfigClass, "loadProxyList")
            if (loadProxyMethod != null) {
                XposedBridge.hookMethod(loadProxyMethod, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        injectProxyObject(sharedConfigClass, classLoader)
                    }
                })
            }

            // 3. Prevent disabling proxy
            val saveProxyMethod = findMethod(sharedConfigClass, "saveProxyList")
            if (saveProxyMethod != null) {
                XposedBridge.hookMethod(saveProxyMethod, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        injectProxyObject(sharedConfigClass, classLoader)
                    }
                })
            }

            // 4. Hook loadConfiguration
            val loadConfigMethod = findMethod(sharedConfigClass, "loadConfiguration")
            if (loadConfigMethod != null) {
                XposedBridge.hookMethod(loadConfigMethod, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        injectProxyObject(sharedConfigClass, classLoader)
                    }
                })
            }

            // 5. Initial injection attempt
            injectProxyObject(sharedConfigClass, classLoader)

            XposedBridge.log("[$TAG] SharedConfig hooks installed")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] hookTelegramSharedConfig error: ${t.message}")
        }
    }

    private fun injectProxyObject(sharedConfigClass: Class<*>, classLoader: ClassLoader) {
        try {
            val proxyInfoClass = XposedHelpers.findClassIfExists("org.telegram.messenger.SharedConfig\$ProxyInfo", classLoader)
                ?: return

            val proxyList = XposedHelpers.getStaticObjectField(sharedConfigClass, "proxyList") as? MutableList<Any>

            var existingProxy: Any? = null
            if (proxyList != null) {
                for (item in proxyList) {
                    val addr = getProxyAddress(item)
                    val port = getProxyPort(item)
                    if (addr == PROXY_HOST && port == PROXY_PORT) {
                        existingProxy = item
                        break
                    }
                }
            }

            if (existingProxy != null) {
                XposedHelpers.setStaticObjectField(sharedConfigClass, "currentProxy", existingProxy)
            } else {
                val newProxy = createProxyInfo(proxyInfoClass, classLoader)
                if (newProxy != null) {
                    proxyList?.add(0, newProxy)
                    XposedHelpers.setStaticObjectField(sharedConfigClass, "currentProxy", newProxy)
                    XposedBridge.log("[$TAG] Injected 127.0.0.1:$PROXY_PORT into proxyList")
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] injectProxyObject error: ${t.message}")
        }
    }

    private fun getProxyAddress(item: Any): String? {
        return try {
            XposedHelpers.getObjectField(item, "address") as? String
        } catch (_: Throwable) {
            try {
                val settings = XposedHelpers.getObjectField(item, "settings")
                XposedHelpers.getObjectField(settings, "address") as? String
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun getProxyPort(item: Any): Int {
        return try {
            XposedHelpers.getIntField(item, "port")
        } catch (_: Throwable) {
            try {
                val settings = XposedHelpers.getObjectField(item, "settings")
                XposedHelpers.getIntField(settings, "port")
            } catch (_: Throwable) {
                0
            }
        }
    }

    private fun createProxyInfo(proxyInfoClass: Class<*>, classLoader: ClassLoader): Any? {
        // 1. Standard Telegram ProxyInfo(address, port, username, password, secret)
        try {
            return XposedHelpers.newInstance(proxyInfoClass, PROXY_HOST, PROXY_PORT, "", "", PROXY_SECRET)
        } catch (_: Throwable) {}

        // 2. Try with ProxySettings if modern exteraGram/forks
        val settingsClass = XposedHelpers.findClassIfExists("org.telegram.utils.proxy.ProxySettings", classLoader)
        if (settingsClass != null) {
            try {
                val typeClass = XposedHelpers.findClassIfExists("org.telegram.utils.proxy.ProxySettings\$Type", classLoader)
                val mtprotoType = typeClass?.enumConstants?.firstOrNull { it.toString().contains("MTPROTO", ignoreCase = true) }
                val settingsObj = if (mtprotoType != null) {
                    try {
                        XposedHelpers.newInstance(settingsClass, mtprotoType, PROXY_HOST, PROXY_PORT, "", "", PROXY_SECRET)
                    } catch (_: Throwable) { null }
                } else null

                if (settingsObj != null) {
                    try {
                        return XposedHelpers.newInstance(proxyInfoClass, settingsObj)
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }

        // 3. Fallback: find any constructor accepting (String, Int, ...)
        for (ctor in proxyInfoClass.declaredConstructors) {
            try {
                ctor.isAccessible = true
                val pts = ctor.parameterTypes
                if (pts.size == 5 && pts[0] == String::class.java && (pts[1] == Int::class.javaPrimitiveType || pts[1] == java.lang.Integer::class.java)) {
                    return ctor.newInstance(PROXY_HOST, PROXY_PORT, "", "", PROXY_SECRET)
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun hookConnectionsManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        try {
            val connMgrClass = XposedHelpers.findClassIfExists("org.telegram.tgnet.ConnectionsManager", classLoader)
                ?: return

            // Hook native_setProxySettings if present to override empty or broken settings
            for (m in connMgrClass.declaredMethods) {
                if (m.name == "native_setProxySettings") {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val addr = param.args.getOrNull(1) as? String
                            if (addr.isNullOrBlank()) {
                                // Prevent disabling: force local proxy
                                param.args[1] = PROXY_HOST
                                param.args[2] = PROXY_PORT
                                param.args[3] = ""
                                param.args[4] = ""
                                param.args[5] = PROXY_SECRET
                                XposedBridge.log("[$TAG] native_setProxySettings redirected to local proxy")
                            }
                        }
                    })
                }
            }

            XposedBridge.log("[$TAG] ConnectionsManager hooks installed")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] hookConnectionsManager error: ${t.message}")
        }
    }

    private fun hookSocketConnect(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                Socket::class.java,
                "connect",
                SocketAddress::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val endpoint = param.args[0] as? InetSocketAddress ?: return
                        val host = endpoint.hostString ?: endpoint.address?.hostAddress ?: return

                        if (isTelegramIp(host)) {
                            XposedBridge.log("[$TAG] Intercepted socket to $host:${endpoint.port} -> redirecting to $PROXY_HOST:$PROXY_PORT")
                            param.args[0] = InetSocketAddress(PROXY_HOST, PROXY_PORT)
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] Socket.connect redirection hook installed")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] hookSocketConnect error: ${t.message}")
        }
    }
}
