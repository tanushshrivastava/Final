package com.cs407.afinal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.afinal.R
import com.cs407.afinal.sleep.SleepViewModel
import java.util.concurrent.TimeUnit

@Composable
fun TrendsScreen(viewModel: SleepViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val history = uiState.history

    val totalSleepTimeMillis = history.sumOf { it.plannedWakeMillis - (it.plannedBedTimeMillis ?: 0) }
    val averageSleepTimeMillis = if (history.isNotEmpty()) totalSleepTimeMillis / history.size else 0

    val averageHours = TimeUnit.MILLISECONDS.toHours(averageSleepTimeMillis)
    val averageMinutes = TimeUnit.MILLISECONDS.toMinutes(averageSleepTimeMillis) % 60

    val totalCycles = history.sumOf { entry ->
        val alarm = uiState.alarms.find { it.id == entry.alarmId }
        alarm?.targetCycles ?: 0
    }
    val averageCycles = if (history.isNotEmpty()) totalCycles.toFloat() / history.size else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.sleeping),
            contentDescription = "Sleeping icon",
            modifier = Modifier.size(128.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Average Sleep Time", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("$averageHours hours and $averageMinutes minutes", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Average Sleep Cycles", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(String.format("%.1f cycles", averageCycles), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
