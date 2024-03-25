package com.mybraintech.demosdk

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

class EEGFileProvider : FileProvider() {

    companion object {
        fun shareFile(outputFile: File, activity: Activity) {
            val path = outputFile.path
            Timber.i("output file path = $path")
            val contentUri: Uri = FileProvider.getUriForFile(
                activity,
                "authority.com.mybraintech.demosdk",
                outputFile
            )
            Intent(Intent.ACTION_SEND).apply {
                setDataAndType(contentUri, "text/json")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newRawUri("", contentUri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }.also {
                activity.startActivity(it)
            }
        }
    }
}