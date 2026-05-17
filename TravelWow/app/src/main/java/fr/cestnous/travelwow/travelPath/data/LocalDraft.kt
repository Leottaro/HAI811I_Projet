package fr.cestnous.travelwow.travelPath.data

import fr.cestnous.travelwow.*
import fr.cestnous.travelwow.travelPath.*
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "drafts")
data class LocalDraft(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val stepsJson: String,
    val createdAt: Long,
    val isSynced: Boolean = false
) {
    fun toFirebaseDraft(): FirebaseDraft {
        val type = object : TypeToken<List<TravelStep>>() {}.type
        val travelSteps: List<TravelStep> = Gson().fromJson(stepsJson, type) ?: emptyList()
        return FirebaseDraft(
            id = id,
            title = title,
            description = description,
            steps = travelSteps.map { step ->
                FirebaseStep(
                    id = step.id,
                    name = step.name,
                    category = step.category,
                    latitude = step.latitude,
                    longitude = step.longitude,
                    imageUrls = step.images
                )
            },
            createdAt = createdAt
        )
    }

    companion object {
        fun fromFirebaseDraft(draft: FirebaseDraft, userId: String, isSynced: Boolean = true): LocalDraft {
            val travelSteps = draft.steps.map { s ->
                TravelStep(
                    id = s.id,
                    name = s.name,
                    category = s.category,
                    latitude = s.latitude,
                    longitude = s.longitude,
                    images = s.imageUrls
                )
            }
            return LocalDraft(
                id = draft.id,
                userId = userId,
                title = draft.title,
                description = draft.description,
                stepsJson = Gson().toJson(travelSteps),
                createdAt = draft.createdAt,
                isSynced = isSynced
            )
        }
    }
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getDraftsForUser(userId: String): List<LocalDraft>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: LocalDraft)

    @Delete
    suspend fun deleteDraft(draft: LocalDraft)

    @Query("DELETE FROM drafts WHERE id = :draftId")
    suspend fun deleteByDraftId(draftId: String)

    @Query("SELECT * FROM drafts WHERE isSynced = 0")
    suspend fun getUnsyncedDrafts(): List<LocalDraft>
}
