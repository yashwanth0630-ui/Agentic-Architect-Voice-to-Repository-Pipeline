package com.example.agenticarchitect.data.local

import com.example.agenticarchitect.data.remote.IdeaItem
import com.example.agenticarchitect.data.remote.LaptopAiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * In-memory thread-safe Fake DAO mirroring SQLiteIdeaVaultDao behavior exactly.
 */
class FakeIdeaVaultDao : IdeaVaultDao {
    private val ideasMap = mutableMapOf<String, IdeaVaultEntity>()
    private val tombstones = mutableListOf<Pair<String, String?>>()
    private val _ideasFlow = MutableStateFlow<List<IdeaVaultEntity>>(emptyList())

    private fun updateFlow() {
        _ideasFlow.value = ideasMap.values.sortedByDescending { it.updatedAt }
    }

    override fun getAllIdeas(): Flow<List<IdeaVaultEntity>> = _ideasFlow.asStateFlow()

    override suspend fun getIdeaById(localId: String): IdeaVaultEntity? = ideasMap[localId]

    override suspend fun getPendingSyncIdeas(): List<IdeaVaultEntity> {
        return ideasMap.values.filter {
            it.syncStatus == IdeaSyncStatus.PENDING || it.syncStatus == IdeaSyncStatus.FAILED
        }.sortedBy { it.createdAt }
    }

    override suspend fun insertIdea(idea: IdeaVaultEntity): Long {
        ideasMap[idea.localId] = idea
        updateFlow()
        return 1L
    }

    override suspend fun updateIdea(idea: IdeaVaultEntity): Int {
        return if (ideasMap.containsKey(idea.localId)) {
            ideasMap[idea.localId] = idea
            updateFlow()
            1
        } else 0
    }

    override suspend fun updateSyncStatus(
        localId: String,
        status: IdeaSyncStatus,
        remoteId: String?,
        errorMessage: String?
    ) {
        val existing = ideasMap[localId] ?: return
        ideasMap[localId] = existing.copy(
            syncStatus = status,
            remoteId = remoteId ?: existing.remoteId,
            syncErrorMessage = errorMessage,
            updatedAt = System.currentTimeMillis()
        )
        updateFlow()
    }

    override suspend fun deleteIdea(localId: String): Int {
        val removed = ideasMap.remove(localId)
        updateFlow()
        return if (removed != null) 1 else 0
    }

    override suspend fun recordTombstone(localId: String, remoteId: String?) {
        tombstones.add(Pair(localId, remoteId))
    }

    override suspend fun getPendingTombstones(): List<Pair<String, String?>> {
        return ArrayList(tombstones)
    }

    override suspend fun deleteTombstone(localId: String) {
        tombstones.removeAll { it.first == localId }
    }
}

/**
 * Controllable Fake LaptopAiRepository for simulating offline, errors, timeouts, and success.
 */
class FakeLaptopAiRepository : LaptopAiRepository() {
    var shouldFail: Boolean = false
    var failureException: Exception = IOException("Connection refused: laptop offline")
    val capturedCustomIds = mutableListOf<String>()
    val updatedRemoteIds = mutableListOf<String>()
    val deletedRemoteIds = mutableListOf<String>()

    override suspend fun captureIdeaWithCustomId(
        prompt: String,
        title: String?,
        techStack: String?,
        ideaId: String?
    ): Result<IdeaItem> {
        if (shouldFail) return Result.failure(failureException)
        val finalId = ideaId ?: "server_generated_id"
        capturedCustomIds.add(finalId)
        return Result.success(
            IdeaItem(
                idea_id = finalId,
                title = title ?: "Captured Idea",
                raw_prompt = prompt,
                created_at = "2026-10-11T21:40:00Z",
                updated_at = "2026-10-11T21:40:00Z",
                status = "captured",
                tech_stack = techStack ?: "auto"
            )
        )
    }

    override suspend fun updateIdeaOnLaptop(
        ideaId: String,
        prompt: String?,
        title: String?,
        techStack: String?
    ): Result<IdeaItem> {
        if (shouldFail) return Result.failure(failureException)
        updatedRemoteIds.add(ideaId)
        return Result.success(
            IdeaItem(
                idea_id = ideaId,
                title = title ?: "Updated Idea",
                raw_prompt = prompt ?: "",
                created_at = "2026-10-11T21:40:00Z",
                updated_at = "2026-10-11T21:45:00Z",
                status = "updated",
                tech_stack = techStack ?: "auto"
            )
        )
    }

    override suspend fun deleteIdeaOnLaptop(ideaId: String): Result<Boolean> {
        if (shouldFail) return Result.failure(failureException)
        deletedRemoteIds.add(ideaId)
        return Result.success(true)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class IdeaVaultRepositoryTest {

    private lateinit var fakeDao: FakeIdeaVaultDao
    private lateinit var fakeLaptop: FakeLaptopAiRepository
    private lateinit var testScope: TestScope
    private lateinit var repository: IdeaVaultRepository

    @Before
    fun setUp() {
        fakeDao = FakeIdeaVaultDao()
        fakeLaptop = FakeLaptopAiRepository()
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        repository = IdeaVaultRepository(
            context = null,
            dao = fakeDao,
            laptopRepository = fakeLaptop,
            scope = testScope,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun test1_InsertRetrieveUpdateAndDelete_CRUD() = testScope.runTest {
        // 1. Capture locally
        val idea = repository.captureIdea(prompt = "Build a cross-chain liquidity aggregator", title = "DEX Aggregator")
        assertNotNull(idea.localId)
        assertEquals("DEX Aggregator", idea.title)

        // Verify retrieval
        val retrieved = repository.getIdea(idea.localId)
        assertNotNull(retrieved)
        assertEquals("Build a cross-chain liquidity aggregator", retrieved?.rawPrompt)

        // 2. Update locally
        val updated = repository.updateIdea(idea.localId, prompt = "Build an optimized multi-chain aggregator", title = "DEX Aggregator v2")
        assertNotNull(updated)
        assertEquals("DEX Aggregator v2", updated?.title)
        assertEquals(IdeaSyncStatus.PENDING, updated?.syncStatus)

        // 3. Delete
        val deleted = repository.deleteIdea(idea.localId)
        assertTrue(deleted)
        assertNull(repository.getIdea(idea.localId))
    }

    @Test
    fun test2_MultipleSavedIdeas_OrderedByUpdatedAt() = testScope.runTest {
        repository.captureIdea(prompt = "First Idea", title = "Idea 1")
        Thread.sleep(10)
        repository.captureIdea(prompt = "Second Idea", title = "Idea 2")
        Thread.sleep(10)
        val third = repository.captureIdea(prompt = "Third Idea", title = "Idea 3")

        val list = repository.getAllIdeas().first()
        assertEquals(3, list.size)
        // Newest updated is first
        assertEquals("Idea 3", list[0].title)
    }

    @Test
    fun test3_VoiceAndTextCapture_BothSavedLocallyFirst() = testScope.runTest {
        // Text prompt capture
        val textIdea = repository.captureIdea(prompt = "Text entered prompt for zero-knowledge verifier")
        assertNotNull(textIdea.localId)
        val storedText = repository.getIdea(textIdea.localId)
        assertNotNull("Text idea must exist in local storage immediately", storedText)

        // Voice captured prompt
        val voiceIdea = repository.captureIdea(prompt = "Deploy an automated sniper agent on EVM")
        assertNotNull(voiceIdea.localId)
        val storedVoice = repository.getIdea(voiceIdea.localId)
        assertNotNull("Voice idea must exist in local storage immediately", storedVoice)
        assertTrue(storedVoice?.syncStatus in listOf(IdeaSyncStatus.PENDING, IdeaSyncStatus.SYNCING, IdeaSyncStatus.SYNCED))
    }

    @Test
    fun test4_CaptureWhileBackendUnavailable_PreservesLocalIdea() = testScope.runTest {
        // Laptop bridge is completely offline
        fakeLaptop.shouldFail = true
        fakeLaptop.failureException = IOException("Failed to connect to 192.168.1.5:8000")

        val idea = repository.captureIdea(prompt = "Offline tactical idea", title = "Offline Idea")
        testScheduler.advanceUntilIdle()
        assertNotNull(idea)

        // Verify idea is safely persisted locally despite network failure
        val localRecord = repository.getIdea(idea.localId)
        assertNotNull("Idea must NOT be lost when backend is offline", localRecord)
        assertEquals("Offline Idea", localRecord?.title)
        assertEquals("Offline tactical idea", localRecord?.rawPrompt)

        // Run sync attempt
        val syncSuccess = repository.syncSingleIdea(localRecord!!)
        assertFalse(syncSuccess)

        val failedRecord = repository.getIdea(idea.localId)
        assertEquals(IdeaSyncStatus.FAILED, failedRecord?.syncStatus)
        assertTrue(failedRecord?.syncErrorMessage?.contains("192.168.1.5") == true)
    }

    @Test
    fun test5_SuccessfulSynchronizationAfterReconnection() = testScope.runTest {
        // 1. Capture while offline
        fakeLaptop.shouldFail = true
        val idea = repository.captureIdea(prompt = "Quantum-resistant encryption pipeline", title = "Quantum Sec")
        testScheduler.advanceUntilIdle()
        repository.syncSingleIdea(idea)
        testScheduler.advanceUntilIdle()
        assertEquals(IdeaSyncStatus.FAILED, repository.getIdea(idea.localId)?.syncStatus)

        // 2. Reconnect laptop backend
        fakeLaptop.shouldFail = false
        val syncResult = repository.syncAllPending()
        testScheduler.advanceUntilIdle()

        assertEquals(1, syncResult.totalPending)
        assertEquals(1, syncResult.syncedCount)
        assertEquals(0, syncResult.failedCount)

        val syncedIdea = repository.getIdea(idea.localId)
        assertEquals(IdeaSyncStatus.SYNCED, syncedIdea?.syncStatus)
        assertEquals(idea.localId, syncedIdea?.remoteId)
        assertNull(syncedIdea?.syncErrorMessage)
    }

    @Test
    fun test6_DuplicatePrevention_IdempotentCaptureId() = testScope.runTest {
        fakeLaptop.shouldFail = false
        val idea = repository.captureIdea(prompt = "Unique prompt", title = "Unique Title")
        testScheduler.advanceUntilIdle()

        // Sync twice manually
        repository.syncSingleIdea(idea)
        testScheduler.advanceUntilIdle()
        repository.syncSingleIdea(idea)
        testScheduler.advanceUntilIdle()

        // All capture requests use the same custom ID, guaranteeing no duplicate records
        assertTrue(fakeLaptop.capturedCustomIds.isNotEmpty())
        assertTrue(fakeLaptop.capturedCustomIds.all { it == idea.localId })
    }

    @Test
    fun test7_EditingUnsynchronizedIdea_SyncsLatestContent() = testScope.runTest {
        fakeLaptop.shouldFail = true
        val idea = repository.captureIdea(prompt = "Initial draft", title = "Draft")
        testScheduler.advanceUntilIdle()

        // User edits the idea while still offline
        repository.updateIdea(idea.localId, prompt = "Polished draft with complete architecture specs", title = "Final Specs")
        testScheduler.advanceUntilIdle()

        val current = repository.getIdea(idea.localId)
        assertEquals("Final Specs", current?.title)
        assertEquals("Polished draft with complete architecture specs", current?.rawPrompt)

        // Reconnect and sync
        fakeLaptop.shouldFail = false
        val res = repository.syncAllPending()
        testScheduler.advanceUntilIdle()
        assertEquals(1, res.syncedCount)

        val synced = repository.getIdea(idea.localId)
        assertEquals(IdeaSyncStatus.SYNCED, synced?.syncStatus)
        assertEquals("Final Specs", synced?.title)
    }

    @Test
    fun test8_DeletionWhileOffline_RecordsTombstoneAndSyncsUponReconnect() = testScope.runTest {
        // 1. Idea was previously synced
        val idea = repository.captureIdea(prompt = "To be deleted idea", title = "Temp Idea")
        repository.syncSingleIdea(idea)
        assertEquals(IdeaSyncStatus.SYNCED, repository.getIdea(idea.localId)?.syncStatus)

        // 2. Disconnect and delete offline
        fakeLaptop.shouldFail = true
        repository.deleteIdea(idea.localId)

        // Local idea is deleted immediately
        assertNull(repository.getIdea(idea.localId))

        // Tombstone is recorded in pending tombstones
        val tombstones = fakeDao.getPendingTombstones()
        assertEquals(1, tombstones.size)
        assertEquals(idea.localId, tombstones[0].first)

        // 3. Reconnect and syncAllPending
        fakeLaptop.shouldFail = false
        repository.syncAllPending()

        // Remote deletion was sent to laptop
        assertTrue(fakeLaptop.deletedRemoteIds.contains(idea.localId))
        // Tombstone is cleared
        assertTrue(fakeDao.getPendingTombstones().isEmpty())
    }

    @Test
    fun test9_RetryAfterNetworkTimeout() = testScope.runTest {
        fakeLaptop.shouldFail = true
        fakeLaptop.failureException = java.net.SocketTimeoutException("Read timed out after 5000ms")

        val idea = repository.captureIdea(prompt = "Timeout scenario test", title = "Timeout Idea")
        repository.syncSingleIdea(idea)

        var record = repository.getIdea(idea.localId)
        assertEquals(IdeaSyncStatus.FAILED, record?.syncStatus)
        assertTrue(record?.syncErrorMessage?.contains("timed out") == true)

        // User triggers retry
        fakeLaptop.shouldFail = false
        val retryOk = repository.syncSingleIdea(record!!)
        assertTrue(retryOk)

        record = repository.getIdea(idea.localId)
        assertEquals(IdeaSyncStatus.SYNCED, record?.syncStatus)
    }

    @Test
    fun test10_ReactiveFlowEmitsUpdatesInstantly() = testScope.runTest {
        val flow = repository.getAllIdeas()
        assertTrue(flow.first().isEmpty())

        repository.captureIdea(prompt = "Reactive test", title = "Reactive")
        val updatedList = flow.first()
        assertEquals(1, updatedList.size)
        assertEquals("Reactive", updatedList[0].title)
    }
}
