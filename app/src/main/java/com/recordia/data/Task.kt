package com.recordia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueDateMillis: Long,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val source: String = "voice"
)
