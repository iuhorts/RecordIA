package com.recordia.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(tag: String, msg: String) {
        val line = "[${dateFormat.format(Date())}] $tag: $msg"
        synchronized(logs) {
            logs.add(line)
            if (logs.size > 200) logs.removeAt(0)
        }
    }

    fun getLogs(): List<String> = synchronized(logs) { logs.toList() }

    fun clear() = synchronized(logs) { logs.clear() }
}
