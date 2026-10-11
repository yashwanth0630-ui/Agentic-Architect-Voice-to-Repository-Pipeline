package com.example.agenticarchitect.data.local

/**
 * Synchronization lifecycle states for an idea captured on Android.
 */
enum class IdeaSyncStatus {
    PENDING, // Saved locally, pending upload to laptop
    SYNCING, // Currently transmitting to laptop bridge
    SYNCED,  // Successfully acknowledged and persisted on laptop
    FAILED   // Transmission encountered error, eligible for retry
}

/**
 * Android Idea Vault domain entity.
 */
data class IdeaVaultEntity(
    val localId: String,
    val remoteId: String? = null,
    val title: String,
    val rawPrompt: String,
    val techStack: String = "auto",
    val syncStatus: IdeaSyncStatus = IdeaSyncStatus.PENDING,
    val syncErrorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val blueprintJson: String? = null
)
