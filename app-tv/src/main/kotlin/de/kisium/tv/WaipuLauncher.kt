package de.kisium.tv

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * waipu.tv exposes documented-looking app links on the installed Android TV app.
 * The stream itself stays inside the official app (account + DRM); Kisium TV only
 * switches to the live-TV section.
 */
object WaipuLauncher {
    const val PACKAGE = "de.exaring.waipu"
    private const val LIVE_TV_URI = "waipu://tv"

    fun openLiveTv(context: Context): Boolean {
        val pm = context.packageManager
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse(LIVE_TV_URI)).apply {
            setPackage(PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (pm.resolveActivity(deepLink, 0) != null) {
            if (runCatching { context.startActivity(deepLink) }.isSuccess) return true
        }

        val fallback = pm.getLeanbackLaunchIntentForPackage(PACKAGE)
            ?: pm.getLaunchIntentForPackage(PACKAGE)
            ?: return false
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(fallback)
            true
        }.getOrDefault(false)
    }

    fun openPlayStore(context: Context): Boolean =
        openUrl(context, "https://play.google.com/store/apps/details?id=$PACKAGE")

    fun openAmazonAppStore(context: Context): Boolean =
        openUrl(context, "https://www.amazon.de/gp/mas/dl/android?p=$PACKAGE")

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
