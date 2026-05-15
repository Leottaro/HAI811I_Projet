package fr.cestnous.travelwow

import androidx.room.*
import com.google.firebase.Timestamp
import java.util.Date

@Entity(tableName = "favorite_posts")
data class FavoritePost(
    @PrimaryKey val id: String,
    val authorId: String,
    val title: String,
    val locationName: String,
    val description: String?,
    val mainImageUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
    val durationMinutes: Int,
    val likesCount: Int,
    val commentsCount: Int,
    val createdAt: Long, // Store as timestamp millis
    val tags: List<String> = emptyList()
) {
    fun toFirebasePost(): FirebasePost {
        return FirebasePost(
            id = id,
            authorId = authorId,
            title = title,
            locationName = locationName,
            description = description,
            mainImageUrl = mainImageUrl,
            latitude = latitude,
            longitude = longitude,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            likesCount = likesCount,
            commentsCount = commentsCount,
            createdAt = Timestamp(Date(createdAt)),
            tags = tags
        )
    }

    companion object {
        fun fromFirebasePost(post: FirebasePost): FavoritePost {
            return FavoritePost(
                id = post.id,
                authorId = post.authorId,
                title = post.title,
                locationName = post.locationName,
                description = post.description,
                mainImageUrl = post.mainImageUrl,
                latitude = post.latitude,
                longitude = post.longitude,
                distanceKm = post.distanceKm,
                durationMinutes = post.durationMinutes,
                likesCount = post.likesCount,
                commentsCount = post.commentsCount,
                createdAt = post.createdAt.toDate().time,
                tags = post.tags
            )
        }
    }
}

@Dao
interface FavoritePostDao {
    @Query("SELECT * FROM favorite_posts ORDER BY createdAt DESC")
    suspend fun getAllFavorites(): List<FavoritePost>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(post: FavoritePost)

    @Delete
    suspend fun deleteFavorite(post: FavoritePost)

    @Query("DELETE FROM favorite_posts WHERE id = :postId")
    suspend fun deleteByPostId(postId: String)
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_posts WHERE id = :postId)")
    suspend fun isFavorite(postId: String): Boolean

    @Query("DELETE FROM favorite_posts")
    suspend fun clearAll()
}

class Converters {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",")
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        return list?.joinToString(",") ?: ""
    }
}

@Database(entities = [FavoritePost::class, LocalDraft::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TravelWowDatabase : RoomDatabase() {
    abstract fun favoritePostDao(): FavoritePostDao
    abstract fun draftDao(): DraftDao

    companion object {
        @Volatile
        private var INSTANCE: TravelWowDatabase? = null

        fun getDatabase(context: android.content.Context): TravelWowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TravelWowDatabase::class.java,
                    "travelwow_database"
                )
                .fallbackToDestructiveMigration() // For development, we can wipe the DB if version changes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
