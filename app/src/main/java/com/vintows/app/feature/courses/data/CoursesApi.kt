package com.vintows.app.feature.courses.data

import com.vintows.app.core.network.ApiEnvelope
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Read-only course-builder endpoints, called with the same parameters as the web app
 * (`course-builder.service` in the web bundle). Null query values are left out of the URL.
 */
interface CoursesApi {
    /** Which hierarchy levels this organisation uses (Program → Subject → Chapter → Lesson → Topic on QA). */
    @GET("course-builder/level-config")
    suspend fun levelConfig(): ApiEnvelope<LevelConfigDto>

    /** Nodes of one level. Children are filtered with the parent's FK column, e.g. `parentFk=program_id`. */
    @GET("course-builder/nodes")
    suspend fun nodes(
        @Query("tableName") tableName: String,
        @Query("parentFk") parentFk: String? = null,
        @Query("parentId") parentId: String? = null,
    ): ApiEnvelope<List<JsonObject>>

    /** Topics under a node (QA has none yet, so rows are mapped tolerantly from the `topics` columns). */
    @GET("course-builder/topics/by-node")
    suspend fun topicsByNode(
        @Query("levelCode") levelCode: String,
        @Query("recordId") recordId: String,
        @Query("programId") programId: String? = null,
    ): ApiEnvelope<List<JsonObject>>

    /** Content items of one type attached to a node. [type] is a [ContentType.apiPath]. */
    @GET("course-builder/content/{type}")
    suspend fun content(
        @Path("type") type: String,
        @Query("levelCode") levelCode: String,
        @Query("recordId") recordId: String,
    ): ApiEnvelope<List<JsonObject>>
}
