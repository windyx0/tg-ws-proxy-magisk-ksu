package com.amurcanov.tgwsproxy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

data class TelegramForkApp(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean,
)

object TelegramForkManager {
    val SUPPORTED_FORKS = listOf(
        "app.exteragram.messenger" to "exteraGram",
        "com.radolyn.ayugram" to "AyuGram",
        "org.telegram.messenger" to "Telegram (Official)",
        "org.thunderdog.challegram" to "Telegram X",
        "tw.nekomimi.nekogram" to "Nekogram",
        "tw.nekomimi.nekogramx" to "Nekogram X",
        "cc.modery.cherrygram" to "Cherrygram",
        "org.telegram.plus" to "Plus Messenger",
        "io.github.nextalone.nagram" to "Nagram",
        "com.iMe.android" to "iMe Messenger",
        "app.nicegram" to "Nicegram",
        "ir.ilmili.telegraph" to "Graph Messenger",
        "org.telegram.bgram" to "BGram",
        "org.telegram.mdgram" to "MDGram",
    )

    fun getInstalledForks(context: Context): List<TelegramForkApp> {
        val pm = context.packageManager
        return SUPPORTED_FORKS.map { (pkg, name) ->
            val installed = try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            TelegramForkApp(pkg, name, installed)
        }
    }

    fun applyProxyToTelegram(
        context: Context,
        url: String,
        preferredPackage: String = "auto",
    ): Boolean {
        val pm = context.packageManager
        val uri = Uri.parse(url)

        when (preferredPackage) {
            "ask" -> {
                // Show Android system app chooser dialog
                val baseIntent = Intent(Intent.ACTION_VIEW, uri)
                val chooser = Intent.createChooser(baseIntent, "Выберите клиент Telegram")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(chooser)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "all" -> {
                val installed = getInstalledForks(context).filter { it.isInstalled }
                if (installed.isEmpty()) return false
                installed.forEach { fork ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage(fork.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // ignore error for single app
                    }
                }
                return true
            }
            "auto", "" -> {
                val installed = getInstalledForks(context).filter { it.isInstalled }
                if (installed.isEmpty()) {
                    // Fallback to generic view intent
                    return try {
                        val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallback)
                        true
                    } catch (e: Exception) {
                        Toast.makeText(context, "Telegram не найден", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
                // Pick the first installed fork
                val target = installed.first()
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(target.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            else -> {
                // Specific package requested (e.g. app.exteragram.messenger)
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(preferredPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    // Fallback if requested package wasn't found
                    applyProxyToTelegram(context, url, "auto")
                }
            }
        }
    }
}
