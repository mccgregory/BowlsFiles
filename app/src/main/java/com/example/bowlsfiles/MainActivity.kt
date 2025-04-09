package com.example.bowlsfiles

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FileManagerScreen()
                }
            }
        }
    }
}

@Composable
fun FileManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileList by remember { mutableStateOf(listOf<File>()) }
    var selectedFileContent by remember { mutableStateOf<String?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var lastConnectedTime by remember { mutableStateOf<String?>(null) }

    // SharedPreferences to retrieve last connection time
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("BowlsFilesPrefs", Context.MODE_PRIVATE)

    // Function to refresh the file list
    fun refreshFileList() {
        fileList = context.filesDir.listFiles()?.filter { it.name.startsWith("B") }?.sortedBy { it.name } ?: emptyList()
    }

    // Function to update connection status
    fun updateConnectionStatus() {
        scope.launch {
            isConnected = isWatchConnected(context)
            val lastTimeMillis = sharedPreferences.getLong("last_connection_time", 0L)
            lastConnectedTime = if (lastTimeMillis > 0) {
                val date = Date(lastTimeMillis)
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
            } else {
                "Never"
            }
        }
    }

    // Periodically check connection status and refresh the file list
    LaunchedEffect(Unit) {
        while (true) {
            updateConnectionStatus()
            refreshFileList()
            delay(5000) // Update every 5 seconds
        }
    }

    // Dialog for viewing file content
    if (selectedFileContent != null) {
        AlertDialog(
            onDismissRequest = { selectedFileContent = null },
            title = { Text("Match Details", fontSize = 20.sp) },
            text = {
                Text(
                    text = selectedFileContent ?: "No content",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                Button(onClick = { selectedFileContent = null }) {
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title and Refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bowls Scorer Files",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = {
                    refreshFileList()
                    updateConnectionStatus()
                },
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text("Refresh")
            }
        }

        // Connection status
        Text(
            text = "Watch Connected: ${if (isConnected) "Yes" else "No"}",
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Last Connected: ${lastConnectedTime ?: "Never"}",
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // File list
        if (fileList.isEmpty()) {
            Text(
                text = "No match files found",
                fontSize = 18.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(fileList) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = file.name,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedFileContent = file.readText()
                                }
                                .padding(end = 8.dp)
                        )
                        Row {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, file.readText())
                                        putExtra(Intent.EXTRA_SUBJECT, "Bowls Scorer Match: ${file.name}")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Match File"))
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Share")
                            }
                            Button(
                                onClick = {
                                    file.delete()
                                    refreshFileList()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}