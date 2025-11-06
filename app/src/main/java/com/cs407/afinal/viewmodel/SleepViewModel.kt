package com.cs407.afinal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.afinal.alarm.AlarmScheduler
import com.cs407.afinal.data.AlarmPreferences
import com.cs407.afinal.data.toAlarmItem
import com.cs407.afinal.data.toFirestoreMap
import com.cs407.afinal.data.toHistoryEntry
import com.cs407.afinal.model.*
import com.cs407.afinal.util.SleepCycleCalculator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SleepViewModel(application: Application) : AndroidViewModel(application) {

    private val alarmPreferences = AlarmPreferences(application)
    private val alarmScheduler = AlarmScheduler(application)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        handleAuthChange(firebaseAuth.currentUser)
    }
    private var alarmsListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null

    init {
        auth.addAuthStateListener(authListener)
        refreshState()
        handleAuthChange(auth.currentUser)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
        detachRemoteListeners()
    }

    fun onModeChanged(newMode: SleepMode) {
        _uiState.update { current ->
            val target = current.targetTime
            current.copy(
                mode = newMode,
                suggestions = suggestionsFor(newMode, target)
            )
        }
    }

    fun onTargetTimeChanged(newTime: LocalTime) {
        _uiState.update { current ->
            current.copy(
                targetTime = newTime,
                suggestions = suggestionsFor(current.mode, newTime)
            )
        }
    }

    fun onSleepNowSelected() {
        val now = ZonedDateTime.now()
        _uiState.update {
            it.copy(
                mode = SleepMode.BED_TIME,
                targetTime = now.toLocalTime(),
                suggestions = SleepCycleCalculator.wakeTimeSuggestions(now)
            )
        }
    }

    fun tryScheduleAlarm(
        triggerAtMillis: Long,
        label: String,
        gentleWake: Boolean,
        cycles: Int?,
        plannedBedTimeMillis: Long?
    ): AlarmScheduleOutcome {
        if (!alarmScheduler.canScheduleExactAlarms()) {
            return AlarmScheduleOutcome.MissingExactAlarmPermission
        }
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return AlarmScheduleOutcome.Error("Selected time is in the past. Please choose a future time.")
        }

        val alarmId = alarmPreferences.nextAlarmId()
        val alarm = AlarmItem(
            id = alarmId,
            triggerAtMillis = triggerAtMillis,
            label = label.ifBlank { defaultLabel() },
            isEnabled = true,
            gentleWake = gentleWake,
            createdAtMillis = System.currentTimeMillis(),
            plannedBedTimeMillis = plannedBedTimeMillis,
            targetCycles = cycles
        )

        viewModelScope.launch(Dispatchers.IO) {
            alarmPreferences.upsertAlarm(alarm)
            alarmScheduler.schedule(alarm)
            syncAlarmToRemote(alarm)
            refreshState("Alarm set for ${formatTime(triggerAtMillis)}")
        }
        return AlarmScheduleOutcome.Success
    }

    fun updateAlarm(
        alarmId: Int,
        triggerAtMillis: Long,
        label: String,
        gentleWake: Boolean,
        plannedBedTimeMillis: Long?
    ): AlarmScheduleOutcome {
        val existing = alarmPreferences.loadAlarms().firstOrNull { it.id == alarmId }
            ?: return AlarmScheduleOutcome.Error("Unable to find alarm to update.")

        if (triggerAtMillis <= System.currentTimeMillis()) {
            return AlarmScheduleOutcome.Error("Selected time is in the past. Please choose a future time.")
        }
        if (!alarmScheduler.canScheduleExactAlarms()) {
            return AlarmScheduleOutcome.MissingExactAlarmPermission
        }

        val updated = existing.copy(
            triggerAtMillis = triggerAtMillis,
            label = label.ifBlank { existing.label },
            gentleWake = gentleWake,
            plannedBedTimeMillis = plannedBedTimeMillis,
            isEnabled = true
        )

        viewModelScope.launch(Dispatchers.IO) {
            alarmPreferences.upsertAlarm(updated)
            alarmScheduler.schedule(updated)
            syncAlarmToRemote(updated)
            refreshState("Updated alarm for ${formatTime(triggerAtMillis)}")
        }
        return AlarmScheduleOutcome.Success
    }

    fun toggleAlarm(alarm: AlarmItem, enabled: Boolean): AlarmScheduleOutcome {
        if (enabled && !alarmScheduler.canScheduleExactAlarms()) {
            return AlarmScheduleOutcome.MissingExactAlarmPermission
        }
        if (enabled && alarm.triggerAtMillis <= System.currentTimeMillis()) {
            return AlarmScheduleOutcome.Error("Alarm time has already passed. Edit the alarm to set a new time.")
        }

        viewModelScope.launch(Dispatchers.IO) {
            val updated = alarm.copy(isEnabled = enabled)
            alarmPreferences.upsertAlarm(updated)
            if (enabled) {
                alarmScheduler.schedule(updated)
            } else {
                alarmScheduler.cancel(alarm.id)
            }
            syncAlarmToRemote(updated)
            refreshState(
                if (enabled) "Alarm enabled for ${formatTime(alarm.triggerAtMillis)}"
                else "Alarm disabled"
            )
        }
        return AlarmScheduleOutcome.Success
    }

    fun deleteAlarm(alarmId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            alarmScheduler.cancel(alarmId)
            alarmPreferences.deleteAlarm(alarmId)
            deleteAlarmFromRemote(alarmId)
            refreshState("Alarm deleted")
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun refreshState(message: String? = null) {
        val alarms = alarmPreferences.loadAlarms().sortedBy { it.triggerAtMillis }
        val history = alarmPreferences.loadHistory().sortedByDescending { it.actualDismissedMillis }
        val currentUserEmail = auth.currentUser?.email
        _uiState.update {
            val suggestions = suggestionsFor(it.mode, it.targetTime)
            it.copy(
                suggestions = suggestions,
                alarms = alarms,
                history = history,
                message = message,
                currentUserEmail = currentUserEmail
            )
        }
    }

    private fun suggestionsFor(mode: SleepMode, targetTime: LocalTime): List<SleepSuggestion> =
        when (mode) {
            SleepMode.WAKE_TIME -> {
                val targetWake = SleepCycleCalculator.normalizeTargetDateTime(mode, targetTime)
                SleepCycleCalculator.bedTimeSuggestions(targetWake)
            }

            SleepMode.BED_TIME -> {
                val bedTarget = SleepCycleCalculator.normalizeTargetDateTime(mode, targetTime)
                SleepCycleCalculator.wakeTimeSuggestions(bedTarget)
            }
        }

    private fun handleAuthChange(user: FirebaseUser?) {
        detachRemoteListeners()
        _uiState.update { it.copy(currentUserEmail = user?.email) }
        if (user == null) {
            refreshState()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { syncLocalToRemote(user) }
            attachRemoteListeners(user)
        }
    }

    private suspend fun syncLocalToRemote(user: FirebaseUser) {
        val userDoc = firestore.collection("users").document(user.uid)
        val alarms = alarmPreferences.loadAlarms()
        val history = alarmPreferences.loadHistory()

        alarms.forEach { alarm ->
            userDoc.collection("alarms")
                .document(alarm.id.toString())
                .set(alarm.toFirestoreMap(), SetOptions.merge())
                .await()
        }

        history.forEach { entry ->
            userDoc.collection("history")
                .document(entry.id.toString())
                .set(entry.toFirestoreMap(), SetOptions.merge())
                .await()
        }
    }

    private fun attachRemoteListeners(user: FirebaseUser) {
        val userDoc = firestore.collection("users").document(user.uid)

        alarmsListener = userDoc.collection("alarms")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val previous = alarmPreferences.loadAlarms()
                val remoteAlarms = snapshot?.documents?.mapNotNull { it.toAlarmItem() } ?: emptyList()
                val remoteIds = remoteAlarms.map { it.id }.toSet()
                alarmPreferences.saveAlarms(remoteAlarms)
                previous.filter { it.id !in remoteIds }.forEach { alarmScheduler.cancel(it.id) }
                remoteAlarms.filter { it.isEnabled }.forEach { alarmScheduler.schedule(it) }
                refreshState()
            }

        historyListener = userDoc.collection("history")
            .orderBy("actualDismissedMillis", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val remoteHistory = snapshot?.documents?.mapNotNull { it.toHistoryEntry() } ?: emptyList()
                alarmPreferences.saveHistory(remoteHistory)
                refreshState()
            }
    }

    private fun detachRemoteListeners() {
        alarmsListener?.remove()
        historyListener?.remove()
        alarmsListener = null
        historyListener = null
    }

    private suspend fun syncAlarmToRemote(alarm: AlarmItem) {
        val user = auth.currentUser ?: return
        firestore.collection("users")
            .document(user.uid)
            .collection("alarms")
            .document(alarm.id.toString())
            .set(alarm.toFirestoreMap(), SetOptions.merge())
            .await()
    }

    private suspend fun deleteAlarmFromRemote(alarmId: Int) {
        val user = auth.currentUser ?: return
        firestore.collection("users")
            .document(user.uid)
            .collection("alarms")
            .document(alarmId.toString())
            .delete()
            .await()
    }

    fun onAlarmDismissed(alarmId: Int, entry: SleepHistoryEntry?) {
        if (entry == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val user = auth.currentUser
            if (user != null) {
                firestore.collection("users")
                    .document(user.uid)
                    .collection("history")
                    .document(entry.id.toString())
                    .set(entry.toFirestoreMap(), SetOptions.merge())
                    .await()
                alarmPreferences.loadAlarms()
                    .firstOrNull { it.id == alarmId }
                    ?.let { syncAlarmToRemote(it) }
            }
            refreshState()
        }
    }

    private fun formatTime(triggerAtMillis: Long): String {
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return formatter.format(triggerAtMillis)
    }

    private fun defaultLabel(): String = "Alarm"
}

sealed class AlarmScheduleOutcome {
    object Success : AlarmScheduleOutcome()
    object MissingExactAlarmPermission : AlarmScheduleOutcome()
    data class Error(val reason: String) : AlarmScheduleOutcome()
}

data class SleepUiState(
    val mode: SleepMode = SleepMode.WAKE_TIME,
    val targetTime: LocalTime = LocalTime.now().plusMinutes(30).withSecond(0).withNano(0),
    val suggestions: List<SleepSuggestion> = emptyList(),
    val alarms: List<AlarmItem> = emptyList(),
    val history: List<SleepHistoryEntry> = emptyList(),
    val message: String? = null,
    val currentUserEmail: String? = null
)
