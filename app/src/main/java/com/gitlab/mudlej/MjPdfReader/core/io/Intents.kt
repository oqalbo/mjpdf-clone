// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

fun plainTextShareIntent(chooserTitle: String, text: String): Intent {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "text/plain"
    intent.putExtra(Intent.EXTRA_TEXT, text)
    return Intent.createChooser(intent, chooserTitle)
}
fun fileShareIntent(chooserTitle: String, fileName: String, fileUri: Uri): Intent {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "application/pdf"
    intent.putExtra(Intent.EXTRA_STREAM, fileUri)
    intent.clipData = ClipData(fileName, arrayOf("application/pdf"), ClipData.Item(fileUri))
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(intent, chooserTitle)
}
fun imageShareIntent(chooserTitle: String, fileName: String, fileUri: Uri): Intent {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "image/*"
    intent.putExtra(Intent.EXTRA_STREAM, fileUri)
    intent.clipData = ClipData(fileName, arrayOf("image/*"), ClipData.Item(fileUri))
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(intent, chooserTitle)
}
fun navIntent(context: Context, activity: Class<*>) = Intent(context, activity)

fun linkIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

fun browserLinkIntent(url: String): Intent =
    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER).setData(Uri.parse(url))

fun emailIntent(emailAddress: String, subject: String, text: String): Intent {
    val email = Intent(Intent.ACTION_SENDTO)
    email.data = Uri.parse("mailto:$emailAddress")
    email.putExtra(Intent.EXTRA_SUBJECT, subject)
    email.putExtra(Intent.EXTRA_TEXT, text)
    return email
}

fun restartApplication(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(launchIntent)
    Runtime.getRuntime().exit(0)
}
