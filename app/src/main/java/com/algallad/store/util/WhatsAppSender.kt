package com.algallad.store.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

object WhatsAppSender {
    private val packages = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** Normalizes a phone number to international format (Egypt: 01xxxxxxxxx -> 201xxxxxxxxx). */
    fun normalize(phone: String): String {
        var d = phone.filter { it.isDigit() }
        if (d.startsWith("00")) d = d.drop(2)
        if (d.startsWith("01") && d.length == 11) d = "2$d"
        return d
    }

    private fun installed(ctx: Context, pkg: String) =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun uriFor(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

    /** Opens the seller WhatsApp chat with the PDF attached; the buyer taps send. */
    fun sendPdfToSeller(ctx: Context, file: File, sellerPhone: String, text: String) {
        val uri = uriFor(ctx, file)
        val digits = normalize(sellerPhone)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra("jid", "$admin@example.com")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        for (pkg in packages) {
            if (installed(ctx, pkg)) {
                val ok = runCatching { ctx.startActivity(Intent(base).setPackage(pkg)) }.isSuccess
                if (ok) return
            }
        }
        chooser(ctx, base)
    }

    /** Generic PDF share sheet (any app). */
    fun sharePdf(ctx: Context, file: File) {
        val uri = uriFor(ctx, file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        chooser(ctx, i)
    }

    /** Opens a WhatsApp chat with a given number (admin -> customer). */
    fun openChat(ctx: Context, phone: String, text: String = "") {
        val url = "https://wa.me/${normalize(phone)}" + if (text.isNotBlank()) "?text=${Uri.encode(text)}" else ""
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun chooser(ctx: Context, intent: Intent) {
        val c = Intent.createChooser(Intent(intent).apply { setPackage(null) }, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(c) }
    }
}
