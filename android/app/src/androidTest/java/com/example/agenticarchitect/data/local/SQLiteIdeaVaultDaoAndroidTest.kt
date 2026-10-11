package com.example.agenticarchitect.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SQLiteIdeaVaultDaoAndroidTest {

    private lateinit var context: android.content.Context
    private lateinit var testDbName: String
    private lateinit var dbHelper: IdeaDatabaseOpenHelper
    private lateinit var dao: SQLiteIdeaVaultDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDbName = "test_idea_vault_${System.currentTimeMillis()}.db"
        dbHelper = IdeaDatabaseOpenHelper(context, testDbName)
        dao = SQLiteIdeaVaultDao(dbHelper)
    }

    @After
    fun tearDown() {
        dbHelper.close()
        val dbFile = context.getDatabasePath(testDbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun testRealDatabase_InsertAndRetrieve() = runBlocking {
        val idea = IdeaVaultEntity(
            localId = "local_101",
            title = "Decentralized Oracle",
            rawPrompt = "Build low-latency EVM oracle with SGX enclave",
            techStack = "fastapi",
            syncStatus = IdeaSyncStatus.PENDING
        )

        val rowId = dao.insertIdea(idea)
        assertTrue(rowId > 0)

        val retrieved = dao.getIdeaById("local_101")
        assertNotNull(retrieved)
        assertEquals("Decentralized Oracle", retrieved?.title)
        assertEquals("Build low-latency EVM oracle with SGX enclave", retrieved?.rawPrompt)
        assertEquals(IdeaSyncStatus.PENDING, retrieved?.syncStatus)
    }

    @Test
    fun testRealDatabase_PersistenceAfterClosingAndReopening() = runBlocking {
        // 1. Insert idea
        val idea = IdeaVaultEntity(
            localId = "persist_202",
            title = "Persistent Storage Idea",
            rawPrompt = "Must survive database close and reopen",
            syncStatus = IdeaSyncStatus.SYNCED,
            remoteId = "remote_202"
        )
        dao.insertIdea(idea)

        // 2. Explicitly close database
        dbHelper.close()

        // 3. Reopen with a new Helper and DAO instance on the same file
        val reopenedHelper = IdeaDatabaseOpenHelper(context, testDbName)
        val reopenedDao = SQLiteIdeaVaultDao(reopenedHelper)

        try {
            val loaded = reopenedDao.getIdeaById("persist_202")
            assertNotNull("Idea MUST persist across SQLite closing and reopening", loaded)
            assertEquals("Persistent Storage Idea", loaded?.title)
            assertEquals("remote_202", loaded?.remoteId)
            assertEquals(IdeaSyncStatus.SYNCED, loaded?.syncStatus)
        } finally {
            reopenedHelper.close()
        }
    }

    @Test
    fun testRealDatabase_UpdateAndSyncStatusTransitions() = runBlocking {
        val idea = IdeaVaultEntity(
            localId = "trans_303",
            title = "Status Transition Idea",
            rawPrompt = "Track state machine transitions",
            syncStatus = IdeaSyncStatus.PENDING
        )
        dao.insertIdea(idea)

        // Update content
        val updated = idea.copy(title = "Updated Title", rawPrompt = "Updated Prompt")
        val affectedRows = dao.updateIdea(updated)
        assertEquals(1, affectedRows)

        // Transition to SYNCING
        dao.updateSyncStatus("trans_303", IdeaSyncStatus.SYNCING)
        var current = dao.getIdeaById("trans_303")
        assertEquals(IdeaSyncStatus.SYNCING, current?.syncStatus)

        // Transition to SYNCED with server remote ID
        dao.updateSyncStatus("trans_303", IdeaSyncStatus.SYNCED, remoteId = "server_id_999")
        current = dao.getIdeaById("trans_303")
        assertEquals(IdeaSyncStatus.SYNCED, current?.syncStatus)
        assertEquals("server_id_999", current?.remoteId)
    }

    @Test
    fun testRealDatabase_OfflineTombstonesQueue() = runBlocking {
        // Record offline deletion tombstone
        dao.recordTombstone("del_404", "remote_404")

        val tombstones = dao.getPendingTombstones()
        assertEquals(1, tombstones.size)
        assertEquals("del_404", tombstones[0].first)
        assertEquals("remote_404", tombstones[0].second)

        // Delete tombstone once synced
        dao.deleteTombstone("del_404")
        assertTrue(dao.getPendingTombstones().isEmpty())
    }

    @Test
    fun testRealDatabase_ReactiveFlowUpdates() = runBlocking {
        val flow = dao.getAllIdeas()
        val initial = flow.first()
        val initialSize = initial.size

        dao.insertIdea(
            IdeaVaultEntity(
                localId = "flow_505",
                title = "Flow Trigger",
                rawPrompt = "Verify reactive emission"
            )
        )

        val updated = flow.first()
        assertEquals(initialSize + 1, updated.size)
        assertEquals("flow_505", updated[0].localId)
    }
}
