package com.srgroup.healthassistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.srgroup.healthassistant.data.model.AdminAuth
import com.srgroup.healthassistant.data.model.ChatMessage
import com.srgroup.healthassistant.data.model.ClinicSubscription
import com.srgroup.healthassistant.data.model.Doctor
import com.srgroup.healthassistant.data.model.DoctorPatientAssignment
import com.srgroup.healthassistant.data.model.FollowUpDraft
import com.srgroup.healthassistant.data.model.HealthRecord
import com.srgroup.healthassistant.data.model.MedicationSchedule
import com.srgroup.healthassistant.data.model.MedicationTakenLog
import com.srgroup.healthassistant.data.model.PatientProfile
import com.srgroup.healthassistant.data.model.VitalLog
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version reset to 1: this app has never shipped to a real device, so
 * versions 2-6 from earlier development never existed as an installed
 * schema anywhere - there's nothing to migrate FROM. Writing Migration
 * objects for those transient dev-only versions would mean fabricating
 * SQL for schemas (v3, v4 especially) that were never fully defined in
 * code. Starting the real version history at 1, here, with the final
 * entity set, is the honest move. From this point on - the first
 * version that could actually be installed - every schema change must
 * ship a real Migration; exportSchema=true (below, and the
 * room.schemaLocation arg in app/build.gradle.kts) keeps a JSON record
 * of each version to write those migrations against and test them.
 */
@Database(
    entities = [
        PatientProfile::class,
        VitalLog::class,
        MedicationSchedule::class,
        HealthRecord::class,
        Doctor::class,
        DoctorPatientAssignment::class,
        FollowUpDraft::class,
        ClinicSubscription::class,
        AdminAuth::class,
        MedicationTakenLog::class,
        ChatMessage::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    // Step 1
    abstract fun patientProfileDao(): PatientProfileDao
    abstract fun vitalLogDao(): VitalLogDao
    abstract fun medicationScheduleDao(): MedicationScheduleDao
    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun medicationTakenLogDao(): MedicationTakenLogDao

    // Step 2
    abstract fun doctorDao(): DoctorDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun followUpDraftDao(): FollowUpDraftDao

    // Step 3
    abstract fun clinicSubscriptionDao(): ClinicSubscriptionDao
    abstract fun adminAuthDao(): AdminAuthDao

    // Chat history
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_message (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        patientId INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        isUser INTEGER NOT NULL,
                        urgency TEXT,
                        createdAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(patientId) REFERENCES patient_profile(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS idx_chat_message_patientId 
                    ON chat_message(patientId)
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "health_assistant.db"
                )
                .addMigrations(MIGRATION_1_2)
                // No fallbackToDestructiveMigration(): from v1 onward every
                // schema bump needs an explicit Migration added to this
                // builder (e.g. .addMigrations(MIGRATION_1_2)), or Room
                // throws IllegalStateException on mismatch instead of
                // silently wiping the DB - a crash you'll notice in testing
                // is much safer than a wipe a real patient hits in the field.
                .build().also { INSTANCE = it }
            }
    }
}
