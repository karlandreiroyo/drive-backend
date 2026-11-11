package com.example.d_r_i_v_e

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.d_r_i_v_e.backend.routes.SyncOutcome
import com.example.d_r_i_v_e.backend.routes.SyncRequestDto
import com.example.d_r_i_v_e.backend.routes.SyncResponseDto
import com.example.d_r_i_v_e.database.DriveDatabase
import com.example.d_r_i_v_e.database.TripEntity
import com.example.d_r_i_v_e.repository.DriveRepository
import com.example.d_r_i_v_e.repository.SyncWriteException
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IncidentPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: DriveDatabase
    private var queued = 0

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, DriveDatabase::class.java).build()
        queued = 0
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun trip() = TripEntity(
        id = UUID.randomUUID().toString(), userId = "test-user",
        startTime = 1_000, createdAt = 1_000, status = TripEntity.STATUS_IN_PROGRESS,
        origin = "Test origin", destination = "Test destination"
    )

    private fun repository(upload: suspend (SyncRequestDto) -> SyncOutcome) = DriveRepository(
        context, database, upload, enqueueSync = { queued++ }
    )

    @Test
    fun parentAndIncidentExistBeforeAnyNetworkCall() = runBlocking {
        val trip = trip().copy(isSynced = true)
        val repository = repository { request ->
            assertNotNull(database.tripDao().findById(trip.id))
            val pending = database.tripIncidentDao().unsyncedIncidents(trip.userId).single()
            assertEquals(request.incidents.single().id, pending.id)
            assertEquals(trip.id, request.trips.single().id)
            assertEquals(1, queued)
            SyncOutcome.ConnectionFailed("offline")
        }
        val incident = repository.recordIncident(trip, "Drowsiness", 0.84f, 2_000)
        assertFalse(incident.isSynced)
        assertEquals("Drowsiness", incident.incidentType)
        assertEquals(0.84f, incident.confidence, 0f)
    }

    @Test
    fun serverFailuresAndUnauthorizedLeaveDurableQueuedRows() = runBlocking {
        for (outcome in listOf(
            SyncOutcome.Retryable("HTTP 500"), SyncOutcome.Failed("HTTP 400"),
            SyncOutcome.ConnectionFailed("offline"), SyncOutcome.Unauthorized
        )) {
            val trip = trip()
            val incident = repository { outcome }.recordIncident(trip, "Motion", 0.8f)
            assertFalse(incident.isSynced)
            assertTrue(database.tripIncidentDao().unsyncedIncidentIds(trip.userId).contains(incident.id))
        }
        assertEquals(4, queued)
    }

    @Test
    fun successfulAcknowledgementMarksOnlyTheSavedRows() = runBlocking {
        val trip = trip()
        val incident = repository { request ->
            SyncOutcome.Success(SyncResponseDto(
                success = true,
                tripsSynced = request.trips.map { it.id },
                incidentsSynced = request.incidents.map { it.id },
                emailSentIds = request.incidents.map { it.id }
            ))
        }.recordIncident(trip, "Drowsiness", 0.8f)
        assertTrue(incident.isSynced)
        assertTrue(incident.emailSent)
        assertTrue(database.tripDao().findById(trip.id)!!.isSynced)
        assertTrue(database.tripIncidentDao().unsyncedIncidents(trip.userId).isEmpty())
    }

    @Test
    fun missingAcknowledgementRemainsQueued() = runBlocking {
        val trip = trip()
        val incident = repository { request ->
            SyncOutcome.Success(SyncResponseDto(success = true, tripsSynced = request.trips.map { it.id }))
        }.recordIncident(trip, "Motion", 0.8f)
        assertFalse(incident.isSynced)
        assertTrue(database.tripDao().findById(trip.id)!!.isSynced)
        assertEquals(incident.id, database.tripIncidentDao().unsyncedIncidents(trip.userId).single().id)
    }

    @Test
    fun missingParentAcknowledgementDoesNotMarkIncidentSynced() = runBlocking {
        val trip = trip()
        val incident = repository { request ->
            SyncOutcome.Success(SyncResponseDto(success = true, incidentsSynced = request.incidents.map { it.id }))
        }.recordIncident(trip, "Motion", 0.8f)
        assertFalse(incident.isSynced)
        assertFalse(database.tripDao().findById(trip.id)!!.isSynced)
    }

    @Test
    fun malformedAcknowledgementDoesNotLoseTheLocalRecord() = runBlocking {
        val trip = trip()
        val incident = repository {
            SyncOutcome.Success(Gson().fromJson(
                """{"success":true,"tripsSynced":null,"incidentsSynced":null}""",
                SyncResponseDto::class.java
            ))
        }.recordIncident(trip, "Motion", 0.8f)
        assertFalse(incident.isSynced)
        assertEquals(incident.id, database.tripIncidentDao().unsyncedIncidents(trip.userId).single().id)
    }

    @Test
    fun thrownUploadExceptionKeepsLocalSaveSuccessful() = runBlocking {
        val trip = trip()
        val incident = repository { throw IOException("network lost") }.recordIncident(trip, "Motion", 0.8f)
        assertFalse(incident.isSynced)
        assertEquals(incident.id, database.tripIncidentDao().unsyncedIncidents(trip.userId).single().id)
    }

    @Test
    fun cancelledUploadPropagatesButDoesNotDeleteTheQueuedIncident() = runBlocking {
        val trip = trip()
        try {
            repository { throw CancellationException("cancelled") }.recordIncident(trip, "Motion", 0.8f)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, database.tripIncidentDao().unsyncedIncidents(trip.userId).size)
            assertEquals(1, queued)
        }
    }

    @Test
    fun slowUploadTimesOutWithLocalRecordIntact() = runBlocking {
        val trip = trip()
        val incident = repository { awaitCancellation() }.recordIncident(trip, "Motion", 0.8f)
        assertFalse(incident.isSynced)
        assertEquals(1, database.tripIncidentDao().unsyncedIncidents(trip.userId).size)
    }

    @Test
    fun schedulingFailureDoesNotEraseTheLocalSave() = runBlocking {
        val trip = trip()
        val repository = DriveRepository(
            context, database, upload = { SyncOutcome.ConnectionFailed("offline") },
            enqueueSync = { throw IllegalStateException("scheduler unavailable") }
        )
        val incident = repository.recordIncident(trip, "Motion", 0.8f)
        assertEquals(incident.id, database.tripIncidentDao().unsyncedIncidents(trip.userId).single().id)
    }

    @Test
    fun nonfiniteModelConfidenceDoesNotViolateRoomNotNullColumn() = runBlocking {
        val incident = repository { SyncOutcome.ConnectionFailed("offline") }
            .recordIncident(trip(), "Motion", Float.NaN)
        assertEquals(0f, incident.confidence, 0f)
    }

    @Test
    fun deletedTripCannotReceiveNewIncidents() = runBlocking {
        val trip = trip().copy(isDeleted = true)
        database.tripDao().upsert(trip)
        try {
            repository { error("Must not upload") }.recordIncident(trip, "Motion", 0.8f)
            fail("Expected deleted trip rejection")
        } catch (_: SyncWriteException) {
            assertTrue(database.tripIncidentDao().unsyncedIncidentIds(trip.userId).isEmpty())
            assertEquals(0, queued)
        }
    }

    @Test
    fun databaseInsertFailureRollsBackNewParentAndIsNotReportedAsSaved() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_test_incident BEFORE INSERT ON trip_incidents " +
                "BEGIN SELECT RAISE(ABORT, 'test insert failure'); END"
        )
        val trip = trip()
        try {
            repository { error("Must not upload") }.recordIncident(trip, "Motion", 0.8f)
            fail("Expected SQLite exception")
        } catch (_: SQLiteException) {
            assertNull(database.tripDao().findById(trip.id))
            assertTrue(database.tripIncidentDao().unsyncedIncidentIds(trip.userId).isEmpty())
            assertEquals(0, queued)
        }
    }
}
