package com.example.bowlsfiles

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import android.widget.Toast

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private val matchFiles = mutableStateListOf<MatchFile>()
    private var isRequestingFiles by mutableStateOf(false)

    data class MatchFile(val summary: String, val rawContent: String, val timestamp: Long)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MatchFilesScreen(
                        matchFiles = matchFiles,
                        isRequestingFiles = isRequestingFiles,
                        onRefreshClicked = {
                            matchFiles.clear()
                            isRequestingFiles = true
                            requestMatchFilesFromWatch()
                        },
                        onWatchStatusChecked = { callback ->
                            checkWatchConnection { status ->
                                callback(status ?: "Watch Connected: Unknown")
                            }
                        },
                        onSaveFile = { matchFile ->
                            saveFileToDownloads(matchFile)
                        },
                        onDeleteFile = { matchFile ->
                            matchFiles.remove(matchFile)
                        }
                    )
                }
            }
        }
    }

    private fun checkWatchConnection(onStatusUpdate: (String?) -> Unit) {
        val capabilityClient = Wearable.getCapabilityClient(this)
        capabilityClient.getCapability("wear_os", CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capabilityInfo ->
                onStatusUpdate(
                    if (capabilityInfo.nodes.isNotEmpty()) {
                        requestMatchFilesFromWatch()
                        "Watch Connected: Yes"
                    } else {
                        "Watch Connected: No"
                    }
                )
            }
            .addOnFailureListener {
                onStatusUpdate("Watch Connected: Error")
            }
    }

    private fun requestMatchFilesFromWatch(retryCount: Int = 3, delayMs: Long = 2000) {
        val capabilityClient = Wearable.getCapabilityClient(this)
        capabilityClient.getCapability("wear_os", CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capabilityInfo ->
                val nodeId = capabilityInfo.nodes.firstOrNull()?.id
                if (nodeId != null) {
                    val messageClient = Wearable.getMessageClient(this)
                    messageClient.sendMessage(nodeId, "/request_match_files", byteArrayOf())
                        .addOnSuccessListener {
                            Log.d("PhoneApp", "Requested match files from node $nodeId")
                        }
                        .addOnFailureListener { e ->
                            Log.e("PhoneApp", "Failed to request files, retryCount=$retryCount", e)
                            if (retryCount > 0) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    requestMatchFilesFromWatch(retryCount - 1, delayMs)
                                }, delayMs)
                            } else {
                                isRequestingFiles = false
                            }
                        }
                } else {
                    Log.w("PhoneApp", "No watch nodes found")
                    isRequestingFiles = false
                }
            }
            .addOnFailureListener { e ->
                Log.e("PhoneApp", "Failed to get capability", e)
                isRequestingFiles = false
            }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path?.startsWith("/match_files/") == true) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val matchData = dataMap.getString("match_data")
                    val timestamp = dataMap.getLong("timestamp")
                    val summary = parseMatchData(matchData, timestamp)
                    matchFiles.add(MatchFile(summary, matchData ?: "", timestamp))
                    Log.d("PhoneApp", "Received file: ${item.uri.path}")
                }
            }
        }
        isRequestingFiles = false
    }

    private fun parseMatchData(matchData: String?, timestamp: Long): String {
        if (matchData.isNullOrBlank()) {
            return "Match Summary (Received: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))})\n- Error: No match data available"
        }

        val lines = matchData.split("\n")
        var startTime = "Unknown"
        var endTime = "Unknown"
        var duration = "Unknown"
        var finalScore = "Unknown"

        lines.forEach { line ->
            when {
                line.startsWith("Start Time:") -> startTime = line.removePrefix("Start Time: ").trim()
                line.startsWith("End Time:") -> endTime = line.removePrefix("End Time: ").trim()
                line.startsWith("Elapsed Time:") -> duration = line.removePrefix("Elapsed Time: ").trim()
                line.startsWith("End ") && line.contains(":") -> {
                    if (line.contains("Game Scores")) return@forEach
                    val parts = line.split("End \\d+: \\d+-\\d+".toRegex(), limit = 2)
                    if (parts.size > 1) {
                        val gameScore = parts[1].trim().split("-")
                        if (gameScore.size == 2) {
                            finalScore = "${gameScore[0]}-${gameScore[1]}"
                        }
                    }
                }
            }
        }

        return """
            Match Summary (Received: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))})
            - Start: $startTime
            - End: $endTime
            - Duration: $duration
            - Final Score: $finalScore
        """.trimIndent()
    }

    private fun saveFileToDownloads(matchFile: MatchFile) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fileName = "Match_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(matchFile.timestamp))}.txt"
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { output ->
                output.write(matchFile.rawContent.toByteArray())
            }
            Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("PhoneApp", "Failed to save file: ${e.message}")
            Toast.makeText(this, "Failed to save file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun MatchFilesScreen(
    matchFiles: List<MainActivity.MatchFile>,
    isRequestingFiles: Boolean,
    onRefreshClicked: () -> Unit,
    onWatchStatusChecked: ((String?) -> Unit) -> Unit,
    onSaveFile: (MainActivity.MatchFile) -> Unit,
    onDeleteFile: (MainActivity.MatchFile) -> Unit
) {
    var watchStatus by remember { mutableStateOf<String?>("Checking watch status...") }
    var lastConnected by remember { mutableStateOf("Last Connected: N/A") }

    LaunchedEffect(Unit) {
        onWatchStatusChecked { status ->
            watchStatus = status
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = watchStatus ?: "Watch Status: Unknown",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = when (watchStatus) {
                "Watch Connected: Yes" -> Color.Green
                "Watch Connected: No" -> Color.Red
                "Watch Connected: Error" -> Color.Red
                else -> Color.Gray
            }
        )

        Text(
            text = lastConnected,
            fontSize = 14.sp,
            color = Color.Gray
        )

        Button(
            onClick = {
                onRefreshClicked()
                lastConnected = "Last Connected: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRequestingFiles
        ) {
            if (isRequestingFiles) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Refresh")
            }
        }

        if (matchFiles.isEmpty()) {
            Text(
                text = if (isRequestingFiles) "Loading match files..." else "No match files found",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matchFiles) { matchFile ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = matchFile.summary,
                                fontSize = 14.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = { onSaveFile(matchFile) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Green,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                                ) {
                                    Text("Save", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { onDeleteFile(matchFile) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Red,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                                ) {
                                    Text("Delete", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}