package com.example.agenticarchitect.data.local

import android.content.Context
import com.example.agenticarchitect.data.remote.IdeaItem
import com.example.agenticarchitect.data.remote.LaptopAiRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Result data class for sync operations.
 */
data class SyncResult(
    val totalPending: Int,
    val syncedCount: Int,
    val failedCount: Int,
    val errorSummary: String? = null
)

/**
 * Repository layer orchestrating local Idea Vault storage (Room/SQLite)
 * and safe, idempotent synchronization with the laptop FastAPI backend.
 */
class IdeaVaultRepository(
    private val context: Context? = null,
    private val dao: IdeaVaultDao,
    private val laptopRepository: LaptopAiRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        @Volatile
        private var INSTANCE: IdeaVaultRepository? = null

        fun getInstance(context: Context, laptopRepo: LaptopAiRepository): IdeaVaultRepository {
            return INSTANCE ?: synchronized(this) {
                val dbHelper = IdeaDatabaseOpenHelper(context.applicationContext)
                val dao = SQLiteIdeaVaultDao(dbHelper)
                val repo = IdeaVaultRepository(context.applicationContext, dao, laptopRepo)
                INSTANCE = repo
                repo
            }
        }
    }

    private val syncMutex = Mutex()

    /**
     * Reactive stream of all saved ideas in descending order of update time.
     */
    fun getAllIdeas(): Flow<List<IdeaVaultEntity>> = dao.getAllIdeas()

    /**
     * Retrieves an idea by its local ID.
     */
    suspend fun getIdea(localId: String): IdeaVaultEntity? = dao.getIdeaById(localId)

    /**
     * Captures a new idea and guarantees immediate persistent SQLite storage
     * BEFORE any network attempt is made.
     */
    suspend fun captureIdea(
        prompt: String,
        title: String? = null,
        techStack: String = "auto"
    ): IdeaVaultEntity = withContext(ioDispatcher) {
        val cleanPrompt = prompt.trim()
        val derivedTitle = if (!title.isNullOrBlank()) {
            title.trim()
        } else {
            val words = cleanPrompt.split("\\s+".toRegex()).take(5).joinToString(" ")
            if (words.isNotBlank()) words else "New Idea"
        }

        val localId = "idea_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        val entity = IdeaVaultEntity(
            localId = localId,
            remoteId = null,
            title = derivedTitle,
            rawPrompt = cleanPrompt,
            techStack = techStack,
            syncStatus = IdeaSyncStatus.PENDING,
            syncErrorMessage = null,
            createdAt = now,
            updatedAt = now,
            blueprintJson = null
        )

        dao.insertIdea(entity)

        // Attempt background synchronization immediately if laptop is reachable
        scope.launch {
            syncSingleIdea(entity)
        }

        entity
    }

    /**
     * Updates an existing idea locally and marks it PENDING for re-synchronization.
     */
    suspend fun updateIdea(
        localId: String,
        prompt: String,
        title: String? = null,
        techStack: String? = null
    ): IdeaVaultEntity? = withContext(ioDispatcher) {
        val existing = dao.getIdeaById(localId) ?: return@withContext null
        val cleanPrompt = prompt.trim()
        val updatedTitle = if (!title.isNullOrBlank()) title.trim() else existing.title
        val updatedStack = if (!techStack.isNullOrBlank()) techStack.trim() else existing.techStack

        val updated = existing.copy(
            title = updatedTitle,
            rawPrompt = cleanPrompt,
            techStack = updatedStack,
            syncStatus = IdeaSyncStatus.PENDING,
            syncErrorMessage = null,
            updatedAt = System.currentTimeMillis()
        )

        dao.updateIdea(updated)

        scope.launch {
            syncSingleIdea(updated)
        }

        updated
    }

    /**
     * Deletes an idea locally and queues a tombstone if remoteId exists,
     * ensuring offline deletions are reliably synced to the laptop.
     */
    suspend fun deleteIdea(localId: String): Boolean = withContext(ioDispatcher) {
        val existing = dao.getIdeaById(localId)
        val deleted = dao.deleteIdea(localId) > 0
        if (existing?.remoteId != null) {
            dao.recordTombstone(localId, existing.remoteId)
            scope.launch {
                val res = laptopRepository.deleteIdeaOnLaptop(existing.remoteId)
                if (res.isSuccess) {
                    dao.deleteTombstone(localId)
                }
            }
        }
        deleted
    }

    /**
     * Synchronizes a single idea to the laptop bridge idempotently.
     */
    suspend fun syncSingleIdea(idea: IdeaVaultEntity): Boolean = withContext(ioDispatcher) {
        dao.updateSyncStatus(idea.localId, IdeaSyncStatus.SYNCING)
        try {
            val result = if (idea.remoteId != null) {
                val updateRes = laptopRepository.updateIdeaOnLaptop(
                    ideaId = idea.remoteId,
                    prompt = idea.rawPrompt,
                    title = idea.title,
                    techStack = idea.techStack
                )
                if (updateRes.isFailure) {
                    // Fallback to capture with existing ID in case remote was reset
                    laptopRepository.captureIdeaWithCustomId(
                        prompt = idea.rawPrompt,
                        title = idea.title,
                        techStack = idea.techStack,
                        ideaId = idea.remoteId
                    )
                } else {
                    updateRes
                }
            } else {
                laptopRepository.captureIdeaWithCustomId(
                    prompt = idea.rawPrompt,
                    title = idea.title,
                    techStack = idea.techStack,
                    ideaId = idea.localId
                )
            }

            result.fold(
                onSuccess = { remoteItem ->
                    val remoteId = remoteItem.idea_id.ifEmpty { idea.remoteId ?: idea.localId }
                    dao.updateSyncStatus(
                        localId = idea.localId,
                        status = IdeaSyncStatus.SYNCED,
                        remoteId = remoteId,
                        errorMessage = null
                    )
                    true
                },
                onFailure = { err ->
                    dao.updateSyncStatus(
                        localId = idea.localId,
                        status = IdeaSyncStatus.FAILED,
                        remoteId = idea.remoteId,
                        errorMessage = err.message ?: "Failed to connect to laptop bridge"
                    )
                    false
                }
            )
        } catch (e: Exception) {
            dao.updateSyncStatus(
                localId = idea.localId,
                status = IdeaSyncStatus.FAILED,
                remoteId = idea.remoteId,
                errorMessage = e.message ?: "Unknown sync error"
            )
            false
        }
    }

    /**
     * Performs a full synchronization of all pending/failed ideas and deletions.
     */
    suspend fun syncAllPending(): SyncResult = withContext(ioDispatcher) {
        syncMutex.withLock {
            // 1. Process pending offline deletions (tombstones)
            val tombstones = dao.getPendingTombstones()
            for ((localId, remoteId) in tombstones) {
                if (remoteId != null) {
                    val res = laptopRepository.deleteIdeaOnLaptop(remoteId)
                    if (res.isSuccess) {
                        dao.deleteTombstone(localId)
                    }
                } else {
                    dao.deleteTombstone(localId)
                }
            }

            // 2. Process pending/failed ideas
            val pendingList = dao.getPendingSyncIdeas()
            if (pendingList.isEmpty()) {
                return@withContext SyncResult(0, 0, 0, null)
            }

            var synced = 0
            var failed = 0
            var lastErr: String? = null

            for (item in pendingList) {
                val ok = syncSingleIdea(item)
                if (ok) {
                    synced++
                } else {
                    failed++
                    val current = dao.getIdeaById(item.localId)
                    lastErr = current?.syncErrorMessage ?: "Connection failed"
                }
            }

            SyncResult(
                totalPending = pendingList.size,
                syncedCount = synced,
                failedCount = failed,
                errorSummary = if (failed > 0) lastErr else null
            )
        }
    }
}
