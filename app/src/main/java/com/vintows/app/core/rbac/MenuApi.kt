package com.vintows.app.core.rbac

import com.vintows.app.core.network.ApiEnvelope
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface MenuApi {
    /** Same call the web shell makes after login. Empty ids are sent as empty strings, like the web. */
    @GET("roleaccess/get")
    suspend fun roleAccess(
        @Query("roleId") roleId: Int,
        @Query("grouped") grouped: Boolean = true,
        @Query("entityId") entityId: String = "",
        @Query("institutionId") institutionId: String = "",
        @Query("departmentId") departmentId: String = "",
    ): ApiEnvelope<List<RoleAccessDto>>
}

@Serializable
data class RoleAccessDto(
    val roleId: Int? = null,
    val role: RoleRefDto? = null,
    val menus: List<MenuAccessDto> = emptyList(),
)

@Serializable
data class RoleRefDto(val id: Int? = null, val name: String? = null)

@Serializable
data class MenuAccessDto(
    val roleaccessId: Int? = null,
    val menuId: Int,
    val menu: MenuDto,
    val submenus: List<SubmenuAccessDto> = emptyList(),
)

@Serializable
data class MenuDto(
    val id: Int,
    val menuName: String,
    val name: String? = null,
    val sequenceNumber: Int? = null,
    val urlLink: String? = null,
    val menuType: String? = null,
    val source: MenuSourceDto? = null,
    val parentMenu: ParentMenuDto? = null,
)

@Serializable
data class SubmenuAccessDto(
    val roleaccessId: Int? = null,
    val submenuId: Int,
    val submenu: SubmenuDto,
)

@Serializable
data class SubmenuDto(
    val id: Int,
    val submenuName: String,
    val name: String? = null,
    val urlLink: String? = null,
    val source: MenuSourceDto? = null,
    val parentMenu: ParentMenuDto? = null,
)

@Serializable
data class MenuSourceDto(val name: String? = null, val filesUrl: String? = null)

/** Holds the Bootstrap icon name for the menu, e.g. `bi-star-fill`. */
@Serializable
data class ParentMenuDto(val name: String? = null, val iconUrl: String? = null)
