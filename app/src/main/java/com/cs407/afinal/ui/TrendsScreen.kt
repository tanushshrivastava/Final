package com.cs407.afinal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.afinal.alarm.SleepHistoryEntry
import com.cs407.afinal.sleep.ExportResult
import com.cs407.afinal.sleep.SleepDataExporter
import com.cs407.afinal.sleep.SleepViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history = uiState.history

    val sleepStats = remember(history) { calculateSleepStats(history) }
    val weeklyData = remember(history) { calculateWeeklyData(history) }

    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Sleep Trends", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showExportDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Export Data",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (history.isEmpty()) {
                EmptyTrendsPlaceholder()
            } else {
                SleepScoreCard(sleepStats = sleepStats)
                SleepDurationChart(weeklyData = weeklyData)
                WeeklyStatsCard(sleepStats = sleepStats)
                SleepInsightsCard(sleepStats = sleepStats)
                ExportDataCard(
                    entriesCount = history.size,
                    onExportClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showExportDialog = true
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showExportDialog) {
        ExportDialog(
            entriesCount = history.size,
            isExporting = isExporting,
            onDismiss = { showExportDialog = false },
            onExport = { includeAnalytics ->
                isExporting = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                coroutineScope.launch {
                    val exporter = SleepDataExporter(context)
                    val result = if (includeAnalytics) {
                        exporter.exportWithAnalytics(history, includeAnalytics = true)
                    } else {
                        exporter.exportToCSV(history, includeHeaders = true)
                    }

                    isExporting = false

                    when (result) {
                        is ExportResult.Success -> {
                            showExportDialog = false
                            snackbarHostState.showSnackbar(
                                "Exported ${result.entriesExported} entries successfully!"
                            )

                            // Share the file
                            val shareIntent = exporter.shareCSVFile(result.file)
                            context.startActivity(
                                android.content.Intent.createChooser(shareIntent, "Share Sleep Data")
                            )
                        }
                        is ExportResult.Error -> {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun EmptyTrendsPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Gray.copy(alpha = 0.5f)
            )
            Text("No Sleep Data Yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text(
                "Start using alarms to track your sleep patterns.",
                fontSize = 14.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SleepScoreCard(sleepStats: SleepStats) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedScore by animateFloatAsState(
        targetValue = if (animationPlayed) sleepStats.sleepScore.toFloat() else 0f,
        animationSpec = tween(durationMillis = 1500), label = "score"
    )
    LaunchedEffect(Unit) { animationPlayed = true }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sleep Score", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val strokeWidth = 16.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    drawCircle(color = Color.Gray.copy(alpha = 0.2f), radius = radius, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                    val sweepAngle = (animatedScore / 100f) * 360f
                    val scoreColor = when { sleepStats.sleepScore >= 80 -> Color(0xFF4CAF50); sleepStats.sleepScore >= 60 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }
                    drawArc(brush = Brush.sweepGradient(listOf(scoreColor.copy(alpha = 0.6f), scoreColor)), startAngle = -90f, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = animatedScore.roundToInt().toString(), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(text = getScoreLabel(sleepStats.sleepScore), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = getScoreColor(sleepStats.sleepScore))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ScoreFactorItem(Icons.Default.AccessTime, "Duration", "${sleepStats.averageDurationHours}h")
                ScoreFactorItem(Icons.Default.Schedule, "Consistency", "${sleepStats.consistencyPercentage}%")
                ScoreFactorItem(Icons.Default.WbSunny, "Wake Time", sleepStats.averageWakeTime)
            }
        }
    }
}

@Composable
private fun ScoreFactorItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
    }
}

@Composable
private fun SleepDurationChart(weeklyData: List<DailySleepData>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Weekly Sleep Duration", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F51B5))
            val maxHours = (weeklyData.maxOfOrNull { it.durationHours } ?: 10f).coerceAtLeast(8f)
            Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = size.width / (weeklyData.size * 2f)
                    val spacing = barWidth
                    drawLine(color = Color(0xFF4CAF50).copy(alpha = 0.5f), start = Offset(0f, size.height * (1 - 8f / maxHours)), end = Offset(size.width, size.height * (1 - 8f / maxHours)), strokeWidth = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    weeklyData.forEachIndexed { index, data ->
                        val barHeight = (data.durationHours / maxHours) * size.height
                        val x = spacing + index * (barWidth + spacing)
                        val barColor = when { data.durationHours >= 7.5f -> Color(0xFF4CAF50); data.durationHours >= 6f -> Color(0xFFFFC107); data.durationHours > 0 -> Color(0xFFF44336); else -> Color.Gray.copy(alpha = 0.3f) }
                        drawRoundRect(color = barColor, topLeft = Offset(x, size.height - barHeight), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(8.dp.toPx()))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weeklyData.forEach { data -> Text(text = data.dayLabel, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun WeeklyStatsCard(sleepStats: SleepStats) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("This Week's Stats", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F51B5))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox("Best Day", sleepStats.bestDay, Color(0xFF4CAF50))
                StatBox("Worst Day", sleepStats.worstDay, Color(0xFFF44336))
                StatBox("Total Sleep", "${sleepStats.totalHours}h", Color(0xFF5C6BC0))
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)).padding(12.dp)) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun SleepInsightsCard(sleepStats: SleepStats) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.Insights, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(24.dp))
                Text("Sleep Insights", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
            }
            if (sleepStats.averageDurationHours >= 7.5f) {
                InsightRow(Icons.Default.TrendingUp, "Great job! You're averaging ${sleepStats.averageDurationHours}h per night.", Color(0xFF4CAF50))
            } else {
                InsightRow(Icons.Default.TrendingDown, "Try to get more sleep. You're averaging ${sleepStats.averageDurationHours}h.", Color(0xFFFF9800))
            }
            if (sleepStats.consistencyPercentage >= 80) {
                InsightRow(Icons.Default.TrendingUp, "Your sleep schedule is ${sleepStats.consistencyPercentage}% consistent!", Color(0xFF4CAF50))
            } else {
                InsightRow(Icons.Default.TrendingDown, "Try going to bed at the same time each night.", Color(0xFFFF9800))
            }
        }
    }
}

@Composable
private fun InsightRow(icon: ImageVector, text: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text = text, fontSize = 13.sp, color = Color(0xFF4A148C))
    }
}

@Composable
private fun ExportDataCard(
    entriesCount: Int,
    onExportClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Export Sleep Data",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            text = "$entriesCount entries available",
                            fontSize = 13.sp,
                            color = Color(0xFF388E3C)
                        )
                    }
                }
            }

            Text(
                text = "Download your sleep history as a CSV file to analyze in Excel, share with your doctor, or keep as a backup.",
                fontSize = 13.sp,
                color = Color(0xFF2E7D32),
                lineHeight = 18.sp
            )

            OutlinedButton(
                onClick = onExportClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export & Share")
            }
        }
    }
}

@Composable
private fun ExportDialog(
    entriesCount: Int,
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onExport: (includeAnalytics: Boolean) -> Unit
) {
    var includeAnalytics by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Export Sleep Data",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Export $entriesCount sleep entries to a CSV file.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include Analytics Summary",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Adds average duration, consistency score, and more",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = includeAnalytics,
                            onCheckedChange = { includeAnalytics = it },
                            enabled = !isExporting
                        )
                    }
                }

                if (isExporting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Generating CSV file...", fontSize = 13.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "File will open in share menu",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(includeAnalytics) },
                enabled = !isExporting
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun getScoreLabel(score: Int): String = when { score >= 90 -> "Excellent"; score >= 80 -> "Great"; score >= 70 -> "Good"; score >= 60 -> "Fair"; else -> "Needs Work" }
private fun getScoreColor(score: Int): Color = when { score >= 80 -> Color(0xFF4CAF50); score >= 60 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }

data class SleepStats(val sleepScore: Int = 0, val averageDurationHours: Float = 0f, val averageWakeTime: String = "--:--", val consistencyPercentage: Int = 0, val bestDay: String = "N/A", val worstDay: String = "N/A", val totalHours: Int = 0)
data class DailySleepData(val dayLabel: String, val durationHours: Float)

private fun calculateSleepStats(history: List<SleepHistoryEntry>): SleepStats {
    if (history.isEmpty()) return SleepStats()
    val durations = history.mapNotNull { entry -> entry.plannedBedTimeMillis?.let { bedTime -> val durationMs = entry.actualDismissedMillis - bedTime; if (durationMs > 0) durationMs / (1000 * 60 * 60f) else null } }
    val avgDuration = if (durations.isNotEmpty()) durations.average().toFloat() else 0f
    val totalHours = durations.sum().toInt()
    val wakeTimes = history.map { Instant.ofEpochMilli(it.actualDismissedMillis).atZone(ZoneId.systemDefault()).toLocalTime() }
    val avgWakeMinutes = if (wakeTimes.isNotEmpty()) wakeTimes.map { it.hour * 60 + it.minute }.average().toInt() else 0
    val avgWakeTime = if (avgWakeMinutes > 0) String.format("%02d:%02d", avgWakeMinutes / 60, avgWakeMinutes % 60) else "--:--"
    val durationScore = when { avgDuration >= 8f -> 100; avgDuration >= 7f -> 85; avgDuration >= 6f -> 70; else -> 50 }
    val consistencyScore = if (wakeTimes.size > 1) { val times = wakeTimes.map { it.hour * 60 + it.minute }; val variance = kotlin.math.sqrt(times.map { (it - times.average()) * (it - times.average()) }.average()).toInt(); (100 - variance).coerceIn(50, 100) } else 75
    val sleepScore = (durationScore * 0.6 + consistencyScore * 0.4).toInt()
    val dayOfWeekAvg = history.groupBy { Instant.ofEpochMilli(it.actualDismissedMillis).atZone(ZoneId.systemDefault()).dayOfWeek }.mapValues { (_, entries) -> entries.mapNotNull { e -> e.plannedBedTimeMillis?.let { (e.actualDismissedMillis - it) / (1000 * 60 * 60f) } }.average().toFloat() }
    val bestEntry = dayOfWeekAvg.maxByOrNull { it.value }
    val worstEntry = dayOfWeekAvg.filter { it.value > 0 }.minByOrNull { it.value }
    return SleepStats(sleepScore = sleepScore, averageDurationHours = String.format("%.1f", avgDuration).toFloat(), averageWakeTime = avgWakeTime, consistencyPercentage = consistencyScore, bestDay = bestEntry?.key?.getDisplayName(TextStyle.SHORT, Locale.getDefault()) ?: "N/A", worstDay = worstEntry?.key?.getDisplayName(TextStyle.SHORT, Locale.getDefault()) ?: "N/A", totalHours = totalHours)
}

private fun calculateWeeklyData(history: List<SleepHistoryEntry>): List<DailySleepData> {
    val today = LocalDate.now()
    return (6 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val dayEntries = history.filter { Instant.ofEpochMilli(it.actualDismissedMillis).atZone(ZoneId.systemDefault()).toLocalDate() == date }
        val totalDuration = dayEntries.sumOf { entry -> entry.plannedBedTimeMillis?.let { bedTime -> val d = entry.actualDismissedMillis - bedTime; if (d > 0) d else 0L } ?: 0L }
        DailySleepData(dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1), durationHours = totalDuration / (1000 * 60 * 60f))
    }
}
