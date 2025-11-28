package com.example.omni_link.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A single debug log entry */
data class DebugLogEntry(
        val id: Long = System.currentTimeMillis(),
        val timestamp: Long = System.currentTimeMillis(),
        val level: LogLevel,
        val tag: String,
        val message: String,
        val details: String? = null
) {
    enum class LogLevel {
        INFO, // General information
        PROMPT, // AI prompt being sent
        RESPONSE, // AI response received
        ACTION, // Action being executed
        SUCCESS, // Successful operation
        ERROR, // Error occurred
        DEBUG // Debug/verbose info
    }

    fun formattedTime(): String {
        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return format.format(Date(timestamp))
    }
}

/** State for the debug overlay */
data class DebugOverlayState(
        val isVisible: Boolean = false,
        val isExpanded: Boolean = false,
        val logs: List<DebugLogEntry> = emptyList(),
        val maxLogs: Int = 100,
        val autoScroll: Boolean = true,
        val filterLevel: DebugLogEntry.LogLevel? = null // null = show all
)

/** Singleton manager for debug logs - can be accessed from anywhere */
object DebugLogManager {
    private const val MAX_LOGS = 200

    private val _state = MutableStateFlow(DebugOverlayState())
    val state: StateFlow<DebugOverlayState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val logs: StateFlow<List<DebugLogEntry>> = _logs.asStateFlow()

    /** Add a log entry */
    fun log(level: DebugLogEntry.LogLevel, tag: String, message: String, details: String? = null) {
        val entry = DebugLogEntry(level = level, tag = tag, message = message, details = details)

        _logs.update { currentLogs -> (currentLogs + entry).takeLast(MAX_LOGS) }

        _state.update { it.copy(logs = _logs.value) }
    }

    /** Convenience methods */
    fun info(tag: String, message: String, details: String? = null) =
            log(DebugLogEntry.LogLevel.INFO, tag, message, details)

    fun prompt(tag: String, message: String, details: String? = null) =
            log(DebugLogEntry.LogLevel.PROMPT, tag, message, details)

    fun response(tag: String, message: String, details: String? = null) =
            log(DebugLogEntry.LogLevel.RESPONSE, tag, message, details)

    fun action(tag: String, message: String, details: String? = null) =
            log(DebugLogEntry.LogLevel.ACTION, tag, message, details)

    fun success(tag: String, message: String, details: String? = null) =
            log(DebugLogEntry.LogLevel.SUCCESS, tag, message, details)

    fun error(tag: String, message: String, details: String? = null) =
            log(DebugLogEntry.LogLevel.ERROR, tag, message, details)

    fun debug(tag: String, message: String, details: String? = null) =
            log(DebugLogEntry.LogLevel.DEBUG, tag, message, details)

    /** Toggle overlay visibility */
    fun toggleOverlay() {
        _state.update { it.copy(isVisible = !it.isVisible) }
    }

    /** Show overlay */
    fun showOverlay() {
        _state.update { it.copy(isVisible = true) }
    }

    /** Hide overlay */
    fun hideOverlay() {
        _state.update { it.copy(isVisible = false) }
    }

    /** Toggle expanded state */
    fun toggleExpanded() {
        _state.update { it.copy(isExpanded = !it.isExpanded) }
    }

    /** Clear all logs */
    fun clearLogs() {
        _logs.value = emptyList()
        _state.update { it.copy(logs = emptyList()) }
    }

    /** Set filter level */
    fun setFilter(level: DebugLogEntry.LogLevel?) {
        _state.update { it.copy(filterLevel = level) }
    }

    /** Toggle auto-scroll */
    fun toggleAutoScroll() {
        _state.update { it.copy(autoScroll = !it.autoScroll) }
    }

    /** Get filtered logs */
    fun getFilteredLogs(): List<DebugLogEntry> {
        val filter = _state.value.filterLevel
        return if (filter == null) {
            _logs.value
        } else {
            _logs.value.filter { it.level == filter }
        }
    }
}
