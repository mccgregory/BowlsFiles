package com.example.bowlsfiles

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WearDataListenerService : WearableListenerService() {
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("BowlsFilesPrefs", Context.MODE_PRIVATE)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("WearDataListener", "onDataChanged called with ${dataEvents.count()} events")
        for (event in dataEvents) {
            Log.d("WearDataListener", "Event type: ${event.type}, URI: ${event.dataItem.uri}")
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path == "/match_files") {
                    // Record the last connection time
                    val currentTime = System.currentTimeMillis()
                    sharedPreferences.edit()
                        .putLong("last_connection_time", currentTime)
                        .apply()
                    Log.d("WearDataListener", "Updated last connection time: $currentTime")

                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    val fileName = dataMap.getString("file_name")
                    val fileContent = dataMap.getString("file_content")
                    Log.d("WearDataListener", "Received data - fileName: $fileName, fileContent: $fileContent")

                    if (fileName == null) {
                        Log.e("WearDataListener", "fileName is null, skipping file write")
                        continue
                    }
                    if (fileContent == null) {
                        Log.e("WearDataListener", "fileContent is null for file: $fileName, skipping file write")
                        continue
                    }

                    try {
                        val file = File(filesDir, fileName)
                        file.writeText(fileContent)
                        Log.d("WearDataListener", "Received file: $fileName")
                        Log.d("WearDataListener", "Saved file to ${file.absolutePath}")
                    } catch (e: Exception) {
                        Log.e("WearDataListener", "Failed to save file: $fileName", e)
                    }
                } else {
                    Log.d("WearDataListener", "Ignoring data item with path: ${dataItem.uri.path}")
                }
            }
        }
    }
}