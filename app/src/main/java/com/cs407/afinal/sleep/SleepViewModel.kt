/**
 * ViewModel for sleep tracking UI - simplified to UI state only.
 * All alarm operations delegated to AlarmManager.
 * Location: com.cs407.afinal.sleep.SleepViewModel.kt
 */
package com.cs407.afinal.sleep

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.afinal.alarm.AlarmItem
import com.cs407.afinal.alarm.AlarmManager
import com.cs407.afinal.alarm.AlarmScheduleOutcome
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalTime
import java.time.ZonedDateTime

class SleepViewModel(application: Application) : AndroidViewModel(application) {

    private val alarmManager = AlarmManager(application)
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

    // ==================== UI STATE UPDATES ====================

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
                suggestions = SleepCalculator.wakeTimeSuggestions(now)
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ==================== ALARM OPERATIONS ====================

    fun tryScheduleAlarm(
        triggerAtMillis: Long,
        label: String,
        gentleWake: Boolean,
        cycles: Int?,
        plannedBedTimeMillis: Long?,
        recurringDays: List<Int> = emptyList(),
        parentAlarmId: Int? = null  // NEW: For follow-up alarms
    ): AlarmScheduleOutcome {
        if (!alarmManager.canScheduleExactAlarms()) {
            return AlarmScheduleOutcome.MissingExactAlarmPermission
        }
        if (recurringDays.isEmpty() && triggerAtMillis <= System.currentTimeMillis()) {
            return AlarmScheduleOutcome.Error("Selected time is in the past. Please choose a future time.")
        }

        val alarmId = alarmManager.nextAlarmId()
        val alarm = AlarmItem(
            id = alarmId,
            triggerAtMillis = triggerAtMillis,
            label = label.ifBlank { "Alarm" },
            isEnabled = true,
            gentleWake = gentleWake,
            createdAtMillis = System.currentTimeMillis(),
            plannedBedTimeMillis = plannedBedTimeMillis,
            targetCycles = cycles,
            recurringDays = recurringDays,
            parentAlarmId = parentAlarmId,
            isFollowUp = parentAlarmId != null
        )

        viewModelScope.launch(Dispatchers.IO) {
            alarmManager.upsertAlarm(alarm)
            alarmManager.scheduleAlarm(alarm)
            alarmManager.syncAlarmToFirebase(alarm)
            refreshState("Alarm set for ${SleepCalculator.formatTime(triggerAtMillis)}")
        }
        return AlarmScheduleOutcome.Success
    }

    fun toggleAlarm(alarm: AlarmItem, enabled: Boolean): AlarmScheduleOutcome {
        if (enabled && !alarmManager.canScheduleExactAlarms()) {
            return AlarmScheduleOutcome.MissingExactAlarmPermission
        }
        if (enabled && alarm.triggerAtMillis <= System.currentTimeMillis()) {
            return AlarmScheduleOutcome.Error("Alarm time has already passed. Edit the alarm to set a new time.")
        }

        viewModelScope.launch(Dispatchers.IO) {
            val updated = alarm.copy(isEnabled = enabled)
            alarmManager.upsertAlarm(updated)
            if (enabled) {
                alarmManager.scheduleAlarm(updated)
            } else {
                alarmManager.cancelAlarm(alarm.id)
            }
            alarmManager.syncAlarmToFirebase(updated)
            refreshState(
                if (enabled) "Alarm enabled for ${SleepCalculator.formatTime(alarm.triggerAtMillis)}"
                else "Alarm disabled"
            )
        }
        return AlarmScheduleOutcome.Success
    }

    fun deleteAlarm(alarmId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            alarmManager.deleteAlarm(alarmId)
            alarmManager.deleteAlarmFromFirebase(alarmId)
            refreshState("Alarm deleted")
        }
    }

    fun onAlarmDismissed(alarmId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = alarmManager.markAlarmDismissed(alarmId, System.currentTimeMillis())
            if (entry != null) {
                alarmManager.syncHistoryToFirebase(entry)
            }
            refreshState()
        }
    }

    // ==================== PRIVATE HELPERS ====================

    private fun refreshState(message: String? = null) {
        val alarms = alarmManager.loadAlarms().sortedBy { it.triggerAtMillis }
        val history = alarmManager.loadHistory().sortedByDescending { it.actualDismissedMillis }
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
                val targetWake = SleepCalculator.normalizeTargetDateTime(mode, targetTime)
                SleepCalculator.bedTimeSuggestions(targetWake)
            }
            SleepMode.BED_TIME -> {
                val bedTarget = SleepCalculator.normalizeTargetDateTime(mode, targetTime)
                SleepCalculator.wakeTimeSuggestions(bedTarget)
            }
        }

    // ==================== FIREBASE SYNC ====================

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
        val alarms = alarmManager.loadAlarms()
        val history = alarmManager.loadHistory()

        alarms.forEach { alarm ->
            alarmManager.syncAlarmToFirebase(alarm)
        }

        history.forEach { entry ->
            alarmManager.syncHistoryToFirebase(entry)
        }
    }

    private fun attachRemoteListeners(user: FirebaseUser) {
        val userDoc = firestore.collection("users").document(user.uid)

        alarmsListener = userDoc.collection("alarms")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val previous = alarmManager.loadAlarms()
                val remoteAlarms = snapshot?.documents?.mapNotNull { alarmManager.run { it.toAlarmItem() } } ?: emptyList()
                val remoteIds = remoteAlarms.map { it.id }.toSet()

                alarmManager.saveAlarms(remoteAlarms)

                previous.filter { it.id !in remoteIds }.forEach { alarmManager.cancelAlarm(it.id) }
                remoteAlarms.filter { it.isEnabled }.forEach { alarmManager.scheduleAlarm(it) }

                refreshState()
            }

        historyListener = userDoc.collection("history")
            .orderBy("actualDismissedMillis", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val remoteHistory = snapshot?.documents?.mapNotNull { alarmManager.run { it.toHistoryEntry() } } ?: emptyList()
                alarmManager.saveHistory(remoteHistory)
                refreshState()
            }
    }

    private fun detachRemoteListeners() {
        alarmsListener?.remove()
        historyListener?.remove()
        alarmsListener = null
        historyListener = null
    }
}