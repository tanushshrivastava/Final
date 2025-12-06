package com.cs407.afinal.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Serializable
data class JournalEntry(
    val id: Long = System.currentTimeMillis(),
    val date: String,
    val sleepQuality: Int,
    val moodOnWake: Int,
    val sleepFactors: List<String> = emptyList(),
    val dreamDescription: String = "",
    val dreamTags: List<String> = emptyList(),
    val notes: String = "",
    val hoursSlept: Float = 7f
)

data class SleepFactor(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val color: Color
)

val sleepFactors = listOf(
    SleepFactor("caffeine", "Caffeine", Icons.Default.LocalCafe, Color(0xFF8D6E63)),
    SleepFactor("alcohol", "Alcohol", Icons.Default.LocalBar, Color(0xFFAB47BC)),
    SleepFactor("exercise", "Exercise", Icons.Default.FitnessCenter, Color(0xFF66BB6A)),
    SleepFactor("stress", "Stress", Icons.Default.Psychology, Color(0xFFEF5350)),
    SleepFactor("late_meal", "Late Meal", Icons.Default.Restaurant, Color(0xFFFF7043)),
    SleepFactor("meditation", "Meditation", Icons.Default.Favorite, Color(0xFF7E57C2)),
    SleepFactor("work", "Work Stress", Icons.Default.Work, Color(0xFF78909C)),
    SleepFactor("nap", "Napped", Icons.Default.NightsStay, Color(0xFFFFCA28)),
    SleepFactor("sick", "Feeling Unwell", Icons.Default.MoodBad, Color(0xFFEF5350)),
    SleepFactor("relaxed", "Relaxed", Icons.Default.SentimentVerySatisfied, Color(0xFF4CAF50))
)

val dreamTags = listOf(
    "Vivid", "Lucid", "Nightmare", "Recurring", "Flying", "Falling",
    "Adventure", "Peaceful", "Strange", "Family", "Work", "Travel"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var entries by remember { mutableStateOf(loadJournalEntries(context)) }
    var showNewEntrySheet by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<JournalEntry?>(null) }

    LaunchedEffect(entries) { saveJournalEntries(context, entries) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Sleep Journal", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showNewEntrySheet = true
                },
                containerColor = Color(0xFF5C6BC0)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Entry", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            JournalStatsCard(entries = entries)

            if (entries.isEmpty()) {
                EmptyJournalPlaceholder()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(entries.sortedByDescending { it.date }, key = { it.id }) { entry ->
                        JournalEntryCard(
                            entry = entry,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedEntry = entry
                            },
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                entryToDelete = entry
                            }
                        )
                    }
                }
            }
        }
    }

    if (showNewEntrySheet) {
        NewJournalEntrySheet(
            onDismiss = { showNewEntrySheet = false },
            onSave = { entry ->
                entries = entries + entry
                showNewEntrySheet = false
            }
        )
    }

    selectedEntry?.let { entry ->
        JournalEntryDetailSheet(
            entry = entry,
            onDismiss = { selectedEntry = null }
        )
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Entry?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        entries = entries.filter { it.id != entry.id }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun JournalStatsCard(entries: List<JournalEntry>) {
    val recentEntries = entries.filter { entry ->
        val entryDate = LocalDate.parse(entry.date)
        ChronoUnit.DAYS.between(entryDate, LocalDate.now()) <= 7
    }
    val avgQuality = if (recentEntries.isNotEmpty()) recentEntries.map { it.sleepQuality }.average() else 0.0
    val avgMood = if (recentEntries.isNotEmpty()) recentEntries.map { it.moodOnWake }.average() else 0.0
    val dreamCount = recentEntries.count { it.dreamDescription.isNotEmpty() }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("This Week", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("Avg Quality", String.format("%.1f", avgQuality), Icons.Default.Star, Color(0xFFFFD54F))
                StatItem("Avg Mood", String.format("%.1f", avgMood), Icons.Default.EmojiEmotions, Color(0xFF4CAF50))
                StatItem("Dreams", dreamCount.toString(), Icons.Default.AutoAwesome, Color(0xFF7C4DFF))
                StatItem("Entries", recentEntries.size.toString(), Icons.Default.Book, Color(0xFF5C6BC0))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
    }
}

@Composable
private fun EmptyJournalPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Book, contentDescription = null, tint = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("No Journal Entries", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Start tracking your sleep quality by adding your first entry", fontSize = 14.sp, color = Color.Gray.copy(alpha = 0.6f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun JournalEntryCard(entry: JournalEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = LocalDate.parse(entry.date)
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(date.format(dateFormatter), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(getRelativeDateText(date), fontSize = 12.sp, color = Color.Gray)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { index ->
                        Icon(
                            imageVector = if (index < entry.sleepQuality) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(getMoodIcon(entry.moodOnWake), contentDescription = null, tint = getMoodColor(entry.moodOnWake), modifier = Modifier.size(20.dp))
                    Text(getMoodLabel(entry.moodOnWake), fontSize = 12.sp, color = Color.Gray)
                }
                Text("• ${String.format("%.1f", entry.hoursSlept)}h sleep", fontSize = 12.sp, color = Color.Gray)
            }

            if (entry.sleepFactors.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entry.sleepFactors) { factorId ->
                        val factor = sleepFactors.find { it.id == factorId }
                        factor?.let {
                            Box(
                                modifier = Modifier
                                    .background(it.color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(it.icon, contentDescription = null, tint = it.color, modifier = Modifier.size(14.dp))
                                    Text(it.name, fontSize = 11.sp, color = it.color)
                                }
                            }
                        }
                    }
                }
            }

            if (entry.dreamDescription.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF7C4DFF).copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(16.dp))
                    Text(entry.dreamDescription, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontStyle = FontStyle.Italic)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewJournalEntrySheet(onDismiss: () -> Unit, onSave: (JournalEntry) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    var sleepQuality by remember { mutableIntStateOf(3) }
    var moodOnWake by remember { mutableIntStateOf(3) }
    val selectedFactors = remember { mutableStateListOf<String>() }
    var dreamDescription by remember { mutableStateOf("") }
    val selectedDreamTags = remember { mutableStateListOf<String>() }
    var notes by remember { mutableStateOf("") }
    var hoursSlept by remember { mutableFloatStateOf(7f) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("New Sleep Entry", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")), fontSize = 14.sp, color = Color.Gray)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sleep Quality", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                StarRatingSelector(rating = sleepQuality, onRatingChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sleepQuality = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mood When Waking", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                MoodSelector(selectedMood = moodOnWake, onMoodChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); moodOnWake = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hours Slept: ${String.format("%.1f", hoursSlept)}h", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Slider(
                    value = hoursSlept,
                    onValueChange = { hoursSlept = it },
                    valueRange = 0f..12f,
                    steps = 23,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF5C6BC0), activeTrackColor = Color(0xFF5C6BC0))
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0h", fontSize = 11.sp, color = Color.Gray)
                    Text("12h", fontSize = 11.sp, color = Color.Gray)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Factors Affecting Sleep", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sleepFactors.forEach { factor ->
                        val isSelected = selectedFactors.contains(factor.id)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isSelected) selectedFactors.remove(factor.id) else selectedFactors.add(factor.id)
                            },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(factor.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(factor.name)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = factor.color.copy(alpha = 0.2f), selectedLabelColor = factor.color)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF7C4DFF))
                    Text("Dream Journal", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
                OutlinedTextField(
                    value = dreamDescription,
                    onValueChange = { dreamDescription = it },
                    label = { Text("Describe your dream...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
                if (dreamDescription.isNotEmpty()) {
                    Text("Dream Tags", fontSize = 14.sp, color = Color.Gray)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dreamTags.forEach { tag ->
                            val isSelected = selectedDreamTags.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = { if (isSelected) selectedDreamTags.remove(tag) else selectedDreamTags.add(tag) },
                                label = { Text(tag, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF7C4DFF).copy(alpha = 0.2f), selectedLabelColor = Color(0xFF7C4DFF))
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Additional Notes") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSave(JournalEntry(
                        date = LocalDate.now().toString(),
                        sleepQuality = sleepQuality,
                        moodOnWake = moodOnWake,
                        sleepFactors = selectedFactors.toList(),
                        dreamDescription = dreamDescription,
                        dreamTags = selectedDreamTags.toList(),
                        notes = notes,
                        hoursSlept = hoursSlept
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Entry")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalEntryDetailSheet(entry: JournalEntry, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val date = LocalDate.parse(entry.date)
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(date.format(dateFormatter), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(getRelativeDateText(date), fontSize = 14.sp, color = Color.Gray)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sleep Quality", fontSize = 12.sp, color = Color.Gray)
                        Row { repeat(entry.sleepQuality) { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(20.dp)) } }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Wake Mood", fontSize = 12.sp, color = Color.Gray)
                        Icon(getMoodIcon(entry.moodOnWake), contentDescription = null, tint = getMoodColor(entry.moodOnWake), modifier = Modifier.size(28.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hours Slept", fontSize = 12.sp, color = Color.Gray)
                        Text(String.format("%.1fh", entry.hoursSlept), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (entry.sleepFactors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Factors", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entry.sleepFactors) { factorId ->
                            val factor = sleepFactors.find { it.id == factorId }
                            factor?.let {
                                Card(colors = CardDefaults.cardColors(containerColor = it.color.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp)) {
                                    Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(it.icon, contentDescription = null, tint = it.color, modifier = Modifier.size(16.dp))
                                        Text(it.name, fontSize = 12.sp, color = it.color)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (entry.dreamDescription.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF7C4DFF).copy(alpha = 0.1f)), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF7C4DFF))
                            Text("Dream", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF7C4DFF))
                        }
                        Text(entry.dreamDescription, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontStyle = FontStyle.Italic, lineHeight = 22.sp)
                        if (entry.dreamTags.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(entry.dreamTags) { tag ->
                                    Box(modifier = Modifier.background(Color(0xFF7C4DFF).copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(tag, fontSize = 11.sp, color = Color(0xFF7C4DFF))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (entry.notes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notes", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(entry.notes, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 22.sp)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StarRatingSelector(rating: Int, onRatingChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { star ->
            val isSelected = star <= rating
            val scale by animateFloatAsState(targetValue = if (isSelected) 1.2f else 1f, animationSpec = spring(stiffness = Spring.StiffnessHigh), label = "scale")
            Icon(
                imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Star $star",
                tint = if (isSelected) Color(0xFFFFD54F) else Color.Gray.copy(alpha = 0.3f),
                modifier = Modifier.size(40.dp).scale(scale).clickable { onRatingChange(star) }
            )
        }
    }
}

@Composable
private fun MoodSelector(selectedMood: Int, onMoodChange: (Int) -> Unit) {
    val moods = listOf(
        1 to Icons.Default.SentimentVeryDissatisfied,
        2 to Icons.Default.SentimentDissatisfied,
        3 to Icons.Default.SentimentNeutral,
        4 to Icons.Default.SentimentSatisfied,
        5 to Icons.Default.SentimentVerySatisfied
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        moods.forEach { (mood, icon) ->
            val isSelected = mood == selectedMood
            val scale by animateFloatAsState(targetValue = if (isSelected) 1.3f else 1f, animationSpec = spring(stiffness = Spring.StiffnessHigh), label = "scale")
            Icon(
                imageVector = icon,
                contentDescription = "Mood $mood",
                tint = if (isSelected) getMoodColor(mood) else Color.Gray.copy(alpha = 0.3f),
                modifier = Modifier.size(36.dp).scale(scale).clickable { onMoodChange(mood) }
            )
        }
    }
}

private fun getMoodIcon(mood: Int): ImageVector = when (mood) {
    1 -> Icons.Default.SentimentVeryDissatisfied
    2 -> Icons.Default.SentimentDissatisfied
    3 -> Icons.Default.SentimentNeutral
    4 -> Icons.Default.SentimentSatisfied
    else -> Icons.Default.SentimentVerySatisfied
}

private fun getMoodColor(mood: Int): Color = when (mood) {
    1 -> Color(0xFFF44336)
    2 -> Color(0xFFFF9800)
    3 -> Color(0xFFFFC107)
    4 -> Color(0xFF8BC34A)
    else -> Color(0xFF4CAF50)
}

private fun getMoodLabel(mood: Int): String = when (mood) {
    1 -> "Terrible"
    2 -> "Bad"
    3 -> "Okay"
    4 -> "Good"
    else -> "Great"
}

private fun getRelativeDateText(date: LocalDate): String {
    val daysDiff = ChronoUnit.DAYS.between(date, LocalDate.now())
    return when {
        daysDiff == 0L -> "Today"
        daysDiff == 1L -> "Yesterday"
        daysDiff < 7L -> "$daysDiff days ago"
        else -> "${daysDiff / 7} weeks ago"
    }
}

private fun loadJournalEntries(context: Context): List<JournalEntry> {
    val prefs = context.getSharedPreferences("sleep_journal", Context.MODE_PRIVATE)
    val json = prefs.getString("entries", null) ?: return emptyList()
    return try { Json.decodeFromString<List<JournalEntry>>(json) } catch (e: Exception) { emptyList() }
}

private fun saveJournalEntries(context: Context, entries: List<JournalEntry>) {
    val prefs = context.getSharedPreferences("sleep_journal", Context.MODE_PRIVATE)
    prefs.edit { putString("entries", Json.encodeToString(entries)) }
}

