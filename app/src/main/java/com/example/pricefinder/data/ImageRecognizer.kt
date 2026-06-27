package com.example.pricefinder.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device image labeling via ML Kit. Free and offline. Returns coarse
 * labels (e.g. "Shoe", "Watch") — it identifies the kind of object, not the
 * exact brand/model. The top labels are joined into a text search query.
 */
class ImageRecognizer {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.6f)
            .build()
    )

    data class Labels(val query: String, val all: List<String>)

    suspend fun recognize(context: Context, uri: Uri): Labels {
        val image = InputImage.fromFilePath(context, uri)
        val labels = suspendCancellableCoroutine<List<String>> { cont ->
            labeler.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.sortedByDescending { it.confidence }
                        .map { it.text })
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val top = labels.take(3)
        return Labels(query = top.joinToString(" "), all = labels)
    }
}
