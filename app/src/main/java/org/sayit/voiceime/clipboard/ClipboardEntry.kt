package org.sayit.voiceime.clipboard

import java.util.UUID

data class ClipboardEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
