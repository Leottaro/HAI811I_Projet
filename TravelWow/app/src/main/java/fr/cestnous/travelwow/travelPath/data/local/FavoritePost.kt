package fr.cestnous.travelwow.travelPath.data.local

import android.content.Context
import androidx.room.*
import com.google.firebase.Timestamp
import fr.cestnous.travelwow.travelPath.data.model.FirebasePost
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
    val likedAt: Long = 0L, // Store when the post was liked for sorting
    val categories: List<String> = emptyList()
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
            categories = categories
        )
    }

    companion object {
        fun fromFirebasePost(post: FirebasePost, likedAt: Long = 0L): FavoritePost {
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
                likedAt = likedAt,
                categories = post.categories
            )
        }
    }
}

@Dao
interface FavoritePostDao {
    @Query("SELECT * FROM favorite_posts ORDER BY likedAt DESC")
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

@Database(entities = [FavoritePost::class, LocalDraft::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TravelWowDatabase : RoomDatabase() {
    abstract fun favoritePostDao(): FavoritePostDao
    abstract fun draftDao(): DraftDao

    companion object {
        @Volatile
        private var INSTANCE: TravelWowDatabase? = null

        fun getDatabase(context: Context): TravelWowDatabase {
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
