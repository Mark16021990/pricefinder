package com.example.pricefinder

import android.content.Context
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File

object ImagePicker {
    /** Creates a temp file + content Uri for the camera to write into. */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
    }
}
