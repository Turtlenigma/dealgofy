package com.turtlenigma.dealgofy

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Counts how many times a given circle was tapped on a specific calendar day.
 * Composite primary key (date, circleIndex) so each circle has one row per day.
 */
@Entity(
    tableName = "circle_tap_counts",
    primaryKeys = ["date", "circle_index"]
)
data class CircleTapCount(
    /** ISO calendar day, e.g. "2026-04-16". */
    val date: String,
    @ColumnInfo(name = "circle_index") val circleIndex: Int,
    val count: Int = 0
)
