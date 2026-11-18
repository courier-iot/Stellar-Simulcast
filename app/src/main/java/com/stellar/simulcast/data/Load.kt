package com.stellar.simulcast.data

import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
}

suspend fun <T> attempt(block: suspend () -> T): Load<T> =
    try {
        Load.Ready(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Load.Error(friendlyError(e))
    }

fun friendlyError(e: Exception): String = when (e) {
    is UnknownHostException -> "No connection. Check your internet and try again."
    is SocketTimeoutException -> "AniList took too long to answer. Try again."
    is IOException -> e.message ?: "Network error"
    else -> e.message ?: "Something went wrong"
}
