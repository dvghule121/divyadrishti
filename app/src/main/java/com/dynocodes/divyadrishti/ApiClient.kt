package com.dynocodes.divyadrishti

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.IOException

class ApiClient {
    val client : OkHttpClient

    init {
        // Create OkHttpClient with custom timeout values
        client = OkHttpClient.Builder()
            .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS) // Increase connect timeout to 30 seconds
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS) // Increase read timeout to 30 seconds
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS) // Increase write timeout to 30 seconds
            .build()
    }



    fun sendFrame(frameBytes: ByteArray, callback: (detectedObjects: List<Detection>) -> Unit) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("frame", "frame.jpg", RequestBody.create("image/jpeg".toMediaTypeOrNull(), frameBytes))
            .build()

        val request = Request.Builder()
            .url("https://6862-103-82-43-63.ngrok-free.app/send_frame")
            .post(requestBody)
            .build()

        try {
            GlobalScope.launch {
                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()
                response.body?.close()

                if (jsonResponse != null && jsonResponse.isNotEmpty()) {
                    val detectedObjects = parseJsonResponse(jsonResponse)
                    callback(filterUniqueObjects(detectedObjects))
                } else {
                    Log.e("ApiClient", "Empty or null JSON response")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("ApiClient", "Network error: ${e.message}")
        }
    }

    private fun parseJsonResponse(jsonResponse: String): List<Detection> {
        val detectedObjects = mutableListOf<Detection>()
        val jsonObject = JSONObject(jsonResponse)
        if (jsonObject.has("objects")) {
            val objectsArray = jsonObject.getJSONArray("objects")
            for (i in 0 until objectsArray.length()) {
                val objectJson = objectsArray.getJSONObject(i)
                val bboxArray = objectJson.getJSONArray("bbox")
                val bbox = List(bboxArray.length()) { bboxArray.getInt(it) }
                val label = objectJson.getString("label")
                val position = objectJson.getString("position")
                detectedObjects.add(Detection(bbox, label, position))
            }
        } else if (jsonObject.has("message")) {
            val errorMessage = jsonObject.getString("message")
            Log.d("ApiClient", "Error message: $errorMessage")
        }
        return detectedObjects
    }

    fun mergeDetectedObjects(detections: List<Detection>): List<Detection> {
        val mergedDetections = mutableListOf<Detection>()

        for (detection in detections) {
            var merged = false

            for (mergedDetection in mergedDetections) {
                val overlapThreshold = 0.5 // Adjust this threshold based on your requirement

                val bbox1 = detection.bbox
                val bbox2 = mergedDetection.bbox

                // Calculate the intersection area of the bounding boxes
                val x1 = maxOf(bbox1[0], bbox2[0])
                val y1 = maxOf(bbox1[1], bbox2[1])
                val x2 = minOf(bbox1[2], bbox2[2])
                val y2 = minOf(bbox1[3], bbox2[3])

                val intersectionArea = maxOf(0, x2 - x1 + 1) * maxOf(0, y2 - y1 + 1)

                // Calculate the area of each bounding box
                val area1 = (bbox1[2] - bbox1[0] + 1) * (bbox1[3] - bbox1[1] + 1)
                val area2 = (bbox2[2] - bbox2[0] + 1) * (bbox2[3] - bbox2[1] + 1)

                // Calculate the IoU (Intersection over Union)
                val iou = intersectionArea.toDouble() / (area1 + area2 - intersectionArea)

                // If IoU is greater than the threshold, merge the detections
                if (iou > overlapThreshold) {
                    mergedDetection.bbox = listOf(
                        minOf(bbox1[0], bbox2[0]),
                        minOf(bbox1[1], bbox2[1]),
                        maxOf(bbox1[2], bbox2[2]),
                        maxOf(bbox1[3], bbox2[3])
                    )
                    mergedDetection.label = detection.label // Update the label if necessary
                    mergedDetection.position = detection.position // Update the position if necessary
                    merged = true
                    break
                }
            }

            // If the detection was not merged with any existing detection, add it as a new detection
            if (!merged) {
                mergedDetections.add(detection)
            }
        }

        return mergedDetections
    }
    fun filterUniqueObjects(detections: List<Detection>): List<Detection> {
        val uniqueDetections = mutableSetOf<String>()
        val filteredDetections = mutableListOf<Detection>()

        for (detection in detections) {
            val key = "${detection.label}-${detection.position}"

            // Check if the combination of label and position is already in the set
            if (uniqueDetections.contains(key)) {
                continue
            }

            // Add the combination to the set and the detection to the filtered list
            uniqueDetections.add(key)
            filteredDetections.add(detection)
        }

        return filteredDetections
    }


}

data class Detection(
    var bbox: List<Int>, // List of integers representing bounding box coordinates [xmin, ymin, width, height]
    var label: String,   // Label indicating the detected object type (e.g., "car")
    var position: String // Position of the object (e.g., "mid-center")
)

//Pair<ByteArray?, String?> , return Pair(null, null)