package com.vintows.app.core.rbac

/** A module the user's role can open, as configured in the web Menu Builder. */
data class MenuItem(
    val id: Int,
    val title: String,
    /** Table / module key, e.g. `g_learners`, `roles`. */
    val key: String?,
    /** Bootstrap icon name from the server (`bi-star-fill`), or null. */
    val iconName: String?,
    /** Web route if configured (`/admin/reports-access`, `/app/module-assignment`), else null. */
    val webRoute: String?,
    val children: List<MenuItem> = emptyList(),
)

internal fun List<RoleAccessDto>.toMenuItems(): List<MenuItem> =
    flatMap { it.menus }
        .sortedWith(compareBy(nullsLast()) { it.menu.sequenceNumber })
        .map { access ->
            val menu = access.menu
            MenuItem(
                id = menu.id,
                title = menu.menuName,
                key = menu.name,
                iconName = menu.parentMenu?.iconUrl,
                webRoute = menu.urlLink.nonBlank() ?: menu.source?.filesUrl.nonBlank(),
                children = access.submenus.map { sub ->
                    val submenu = sub.submenu
                    MenuItem(
                        id = submenu.id,
                        title = submenu.submenuName,
                        key = submenu.name,
                        iconName = submenu.parentMenu?.iconUrl,
                        webRoute = submenu.urlLink.nonBlank() ?: submenu.source?.filesUrl.nonBlank(),
                    )
                },
            )
        }

private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
