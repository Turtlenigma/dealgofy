package com.turtlenigma.dealgofy

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CircleTapCountDao {

    @Query("SELECT count FROM circle_tap_counts WHERE date = :date AND circle_index = :index")
    suspend fun getCount(date: String, index: Int): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: CircleTapCount)

    @Query("UPDATE circle_tap_counts SET count = count + 1 WHERE date = :date AND circle_index = :index")
    suspend fun increment(date: String, index: Int)

    /** Atomically ensure a row exists then bump it by one. */
    @Transaction
    suspend fun incrementTap(date: String, index: Int) {
        insertIfAbsent(CircleTapCount(date = date, circleIndex = index, count = 0))
        increment(date, index)
    }

    /** Called by ACTION_DATE_CHANGED receiver to prune stale rows. */
    @Query("DELETE FROM circle_tap_counts WHERE date != :today")
    suspend fun deleteOldEntries(today: String)
}
