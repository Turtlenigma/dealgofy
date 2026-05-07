package com.turtlenigma.dealgofy

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface InterceptEventDao {
    @Insert
    suspend fun insert(event: InterceptEvent)

    /** Deflected = every exit that wasn't the user choosing to enter the app. */
    @Query("SELECT COUNT(*) FROM intercept_events WHERE exit_type != 'ENTER_APP'")
    suspend fun deflectedCount(): Int

    @Query("SELECT COUNT(*) FROM intercept_events")
    suspend fun totalCount(): Int

    /** How many times the user consciously opened [pkg] today (since [dayStartMs]). */
    @Query("""
        SELECT COUNT(*) FROM intercept_events
        WHERE triggered_package = :pkg
          AND exit_type = 'ENTER_APP'
          AND timestamp >= :dayStartMs
    """)
    suspend fun enterAppCountToday(pkg: String, dayStartMs: Long): Int
}
