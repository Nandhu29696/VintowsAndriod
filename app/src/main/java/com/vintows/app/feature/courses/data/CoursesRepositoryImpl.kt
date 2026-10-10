package com.vintows.app.feature.courses.data

import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.network.map
import com.vintows.app.core.session.SessionProvider
import com.vintows.app.feature.courses.domain.ContentItem
import com.vintows.app.feature.courses.domain.ContentType
import com.vintows.app.feature.courses.domain.CourseHierarchy
import com.vintows.app.feature.courses.domain.CourseNode
import com.vintows.app.feature.courses.domain.CoursesRepository
import com.vintows.app.feature.courses.domain.NodeContents
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoursesRepositoryImpl @Inject constructor(
    private val api: CoursesApi,
    private val apiCaller: ApiCaller,
    private val sessions: SessionProvider,
) : CoursesRepository {

    // The level config changes only when an admin edits the hierarchy, so it is fetched once per app run.
    private val hierarchyLock = Mutex()
    private var cachedHierarchy: CourseHierarchy? = null

    override suspend fun hierarchy(): NetworkResult<CourseHierarchy> = hierarchyLock.withLock {
        cachedHierarchy?.let { return NetworkResult.Success(it) }
        apiCaller.call { api.levelConfig() }.map { it.toHierarchy() }.also { result ->
            if (result is NetworkResult.Success && result.data.levels.isNotEmpty()) cachedHierarchy = result.data
        }
    }

    override suspend fun programs(): NetworkResult<List<CourseNode>> {
        val hierarchy = when (val h = hierarchy()) {
            is NetworkResult.Success -> h.data
            is NetworkResult.Error -> return h
        }
        val root = hierarchy.root ?: return NetworkResult.Success(emptyList())
        return apiCaller.call { api.nodes(tableName = root.tableName) }.map { rows -> rows.toNodes(root.code) }
    }

    override suspend fun nodeContents(levelCode: String, nodeId: String, programId: String): NetworkResult<NodeContents> {
        val hierarchy = when (val h = hierarchy()) {
            is NetworkResult.Success -> h.data
            is NetworkResult.Error -> return h
        }
        val level = hierarchy.level(levelCode)
        val childLevel = hierarchy.childOf(levelCode)
        return coroutineScope {
            val children = async {
                when {
                    level == null || childLevel == null -> NetworkResult.Success(emptyList())
                    childLevel.code == CourseHierarchy.TOPIC -> apiCaller.call {
                        api.topicsByNode(levelCode = levelCode, recordId = nodeId, programId = programId)
                    }.map { it.toNodes(CourseHierarchy.TOPIC) }
                    else -> apiCaller.call {
                        api.nodes(
                            tableName = childLevel.tableName,
                            parentFk = CourseHierarchy.foreignKeyOf(level),
                            parentId = nodeId,
                        )
                    }.map { it.toNodes(childLevel.code) }
                }
            }
            val materials = async { materials(levelCode, nodeId) }
            val materialResult = materials.await()
            // Materials are secondary: the node still opens if every material call failed.
            children.await().map { list ->
                NodeContents(
                    childLevel = childLevel,
                    children = list,
                    materials = (materialResult as? NetworkResult.Success)?.data.orEmpty(),
                )
            }
        }
    }

    override suspend fun contentItem(type: ContentType, id: String, levelCode: String, recordId: String): NetworkResult<ContentItem> =
        when (val result = apiCaller.call { api.content(type.apiPath, levelCode, recordId) }) {
            is NetworkResult.Error -> result
            is NetworkResult.Success -> result.data
                .firstNotNullOfOrNull { row -> row.toContentItem(type)?.takeIf { it.id == id } }
                ?.takeIf { includeDrafts() || it.isVisibleToLearners }
                ?.let { NetworkResult.Success(it) }
                ?: NetworkResult.Error(ErrorKind.NotFound, "This item is no longer available.")
        }

    /**
     * All content types for a node, loaded in parallel. A failing type is skipped (one broken table
     * shouldn't hide the rest); only when every type fails is the error returned.
     */
    private suspend fun materials(levelCode: String, nodeId: String): NetworkResult<List<ContentItem>> = coroutineScope {
        val results = ContentType.entries.map { type ->
            async { type to apiCaller.call { api.content(type.apiPath, levelCode, nodeId) } }
        }.awaitAll()
        val successes = results.mapNotNull { (type, result) -> (result as? NetworkResult.Success)?.let { type to it.data } }
        if (successes.isEmpty()) {
            results.first().second as NetworkResult.Error
        } else {
            val showDrafts = includeDrafts()
            NetworkResult.Success(
                successes.flatMap { (type, rows) ->
                    rows.filter { it.isLive() }
                        .sortedBy { it.sequence() }
                        .mapNotNull { it.toContentItem(type) }
                        .filter { showDrafts || it.isVisibleToLearners }
                },
            )
        }
    }

    /** Platform admins (debug preview) also see drafts, labelled as such; learners only see published items. */
    private fun includeDrafts(): Boolean = sessions.current()?.isAdmin == true

    private fun List<JsonObject>.toNodes(levelCode: String): List<CourseNode> =
        filter { it.isLive() }
            .sortedBy { it.sequence() }
            .mapNotNull { it.toCourseNode(levelCode) }
}
