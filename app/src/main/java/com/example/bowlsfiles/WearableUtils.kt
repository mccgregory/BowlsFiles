package com.example.bowlsfiles

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

suspend fun isWatchConnected(context: Context): Boolean {
    return try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.isNotEmpty() // Returns true if at least one node (e.g., the watch) is connected
    } catch (e: Exception) {
        false // Return false if there's an error (e.g., no Wearable devices available)
    }
}