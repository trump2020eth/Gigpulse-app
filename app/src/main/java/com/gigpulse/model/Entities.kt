package com.gigpulse.model
import androidx.room.*
import java.time.Instant

@Entity data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String, // DoorDash / UberEats
    val payoutCents: Int,
    val distanceMiles: Double,
    val startedAt: Long, // epoch millis
    val endedAt: Long
)

@Entity data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // Gas, Tolls, Parking, Other
    val amountCents: Int,
    val at: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity data class MileageEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val miles: Double
)

@Entity data class Hotspot(
    @PrimaryKey val id: String, // name-lat-lng
    val name: String,
    val lat: Double,
    val lng: Double,
    val intensity: Int, // 0..100
    val platform: String = "DoorDash", // or UberEats
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao interface TripDao {
    @Insert suspend fun insert(t: Trip)
    @Query("SELECT * FROM Trip ORDER BY startedAt DESC") suspend fun all(): List<Trip>
    @Query("SELECT SUM(payoutCents) FROM Trip WHERE startedAt BETWEEN :from AND :to")
    suspend fun sumCents(from: Long, to: Long): Int?
}

@Dao interface ExpenseDao {
    @Insert suspend fun insert(e: Expense)
    @Query("SELECT * FROM Expense ORDER BY at DESC") suspend fun all(): List<Expense>
    @Query("SELECT SUM(amountCents) FROM Expense WHERE at BETWEEN :from AND :to")
    suspend fun sumCents(from: Long, to: Long): Int?
}

@Dao interface MileageDao {
    @Insert suspend fun insert(m: MileageEvent)
    @Query("SELECT * FROM MileageEvent ORDER BY startedAt DESC") suspend fun all(): List<MileageEvent>
    @Query("SELECT SUM(miles) FROM MileageEvent WHERE startedAt BETWEEN :from AND :to")
    suspend fun sumMiles(from: Long, to: Long): Double?
}

@Dao interface HotspotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(h: Hotspot)
    @Query("SELECT * FROM Hotspot ORDER BY intensity DESC") suspend fun all(): List<Hotspot>
    @Query("SELECT * FROM Hotspot WHERE intensity >= :threshold ORDER BY intensity DESC")
    suspend fun red(threshold: Int): List<Hotspot>
}

@Database(entities = [Trip::class, Expense::class, MileageEvent::class, Hotspot::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun mileageDao(): MileageDao
    abstract fun hotspotDao(): HotspotDao
}