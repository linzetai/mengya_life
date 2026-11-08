package com.mengyalife.app.care

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mengyalife.app.data.local.MengyaDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), MengyaDatabase::class.java,
    )

    @Test
    fun migrate22To23MigratesSilencedFeedbackToPreference() {
        helper.createDatabase("migration-silence", 22).apply {
            execSQL(
                "INSERT INTO care_opportunities(id,type,source_id,plan_id,goal_id,title,discovered_at," +
                    "window_start,window_end,expires_at,sensitivity,status,dedupe_key,created_at,updated_at) " +
                    "VALUES(1,'PLAN_CHECKIN',1,5,NULL,'作品集',1,1,2,3,'NORMAL','SENT','k',1,1)",
            )
            execSQL(
                "INSERT INTO care_feedback(opportunity_id,kind,created_at) VALUES(1,'SILENCED',1)",
            )
            execSQL(
                "INSERT INTO care_decisions(opportunity_id,action,reason,source_refs,model_ok,created_at) " +
                    "VALUES(1,'SCHEDULE','r','s',1,1)",
            )
            execSQL(
                "INSERT INTO care_decisions(opportunity_id,action,reason,source_refs,model_ok,created_at) " +
                    "VALUES(1,'SEND','r','s',1,2)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(
            "migration-silence", 23, true, MengyaDatabase.MIGRATION_22_23,
        )
        db.query("SELECT topic_key,value,source FROM care_preferences").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("plan:5", c.getString(0))
            assertEquals("SILENCED", c.getString(1))
            assertEquals("USER_EXPLICIT", c.getString(2))
        }
        db.close()
    }

    @Test
    fun migrate21To22AddsNotifiedAt() {
        helper.createDatabase("migration-care", 21).apply {
            execSQL(
                "INSERT INTO care_decisions(opportunity_id,action,reason,source_refs,model_ok,created_at) " +
                    "VALUES(1,'SEND','r','s',1,2)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(
            "migration-care", 22, true, MengyaDatabase.MIGRATION_21_22,
        )
        db.query("SELECT notified_at FROM care_decisions WHERE opportunity_id=1").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(true, c.isNull(0))
        }
        db.close()
    }
}
