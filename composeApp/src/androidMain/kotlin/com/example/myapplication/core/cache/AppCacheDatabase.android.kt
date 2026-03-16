package com.example.myapplication.core.cache

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "cached_shops")
internal data class CachedShopEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val city: String?,
    val openingTime: String,
    val closingTime: String,
    val lat: Double?,
    val lon: Double?,
    val address: String?,
    val enabled: Boolean,
)

@Dao
internal interface CachedShopDao {
    @Query(
        """
        SELECT *
        FROM cached_shops
        WHERE
            :query = '' OR
            name LIKE '%' || :query || '%' COLLATE NOCASE OR
            COALESCE(city, '') LIKE '%' || :query || '%' COLLATE NOCASE OR
            COALESCE(address, '') LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE ASC, id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getShops(
        query: String,
        limit: Int,
        offset: Int,
    ): List<CachedShopEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM cached_shops
        WHERE
            :query = '' OR
            name LIKE '%' || :query || '%' COLLATE NOCASE OR
            COALESCE(city, '') LIKE '%' || :query || '%' COLLATE NOCASE OR
            COALESCE(address, '') LIKE '%' || :query || '%' COLLATE NOCASE
        """,
    )
    suspend fun getCount(query: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShops(shops: List<CachedShopEntity>)
}

@Database(
    entities = [CachedShopEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class AppCacheDatabase : RoomDatabase() {
    abstract fun cachedShopDao(): CachedShopDao

    companion object {
        @Volatile
        private var instance: AppCacheDatabase? = null

        fun getInstance(context: Context): AppCacheDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppCacheDatabase::class.java,
                    "app_cache.db",
                ).build().also { created ->
                    instance = created
                }
            }
        }
    }
}
