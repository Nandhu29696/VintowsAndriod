package com.vintows.app.core.ui

import com.vintows.app.core.network.NetworkResult

/** Load state of one independently loaded part of a screen (a dashboard card, a list). */
sealed interface Section<out T> {
    data object Loading : Section<Nothing>
    data class Loaded<T>(val data: T) : Section<T>
    data class Failed(val message: String) : Section<Nothing>
}

val <T> Section<T>.dataOrNull: T? get() = (this as? Section.Loaded)?.data

fun <T> NetworkResult<T>.toSection(): Section<T> = when (this) {
    is NetworkResult.Success -> Section.Loaded(data)
    is NetworkResult.Error -> Section.Failed(message)
}
