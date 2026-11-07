package de.kisium.tv

import android.content.Context
import android.content.Intent
import android.net.Uri

object DisneyPlusLauncher {
    private val packages = listOf(
        "com.disney.disneyplus",
        "com.disney.disneyplus.androidtv",
    )

    fun open(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in packages) {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
            }
        }
        return false
    }

    fun openPlayStore(context: Context): Boolean =
        openUrl(context, "https://play.google.com/store/apps/details?id=com.disney.disneyplus")

    fun openAmazonAppStore(context: Context): Boolean =
        openUrl(context, "https://www.amazon.de/gp/mas/dl/android?p=com.disney.disneyplus")

    private fun openUrl(context: Context, url: String): Boolean {
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}
