package com.example.isolation.core.result

import android.util.Log

/**
 * Type-safe outcome wrapper for operations across Hollow.
 * Replaces silent `catch (_: Throwable) {}` blocks with traceable success/error states.
 */
sealed interface HollowResult<out T> {

    data class Success<out T>(val data: T) : HollowResult<T>

    data class Error(
        val throwable: Throwable,
        val message: String = throwable.localizedMessage ?: "Unknown error occurred"
    ) : HollowResult<Nothing> {
        init {
            Log.e("HollowResult", "Hollow failure encountered: $message", throwable)
        }
    }

    data object Loading : HollowResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> defaultValue
    }
}

inline fun <T> runCatchingHollow(
    tag: String = "Hollow",
    errorMessage: String? = null,
    block: () -> T
): HollowResult<T> {
    return try {
        HollowResult.Success(block())
    } catch (t: Throwable) {
        val msg = errorMessage ?: t.localizedMessage ?: "Operation failed"
        Log.e(tag, msg, t)
        HollowResult.Error(t, msg)
    }
}

suspend inline fun <T> runSuspendCatchingHollow(
    tag: String = "Hollow",
    errorMessage: String? = null,
    crossinline block: suspend () -> T
): HollowResult<T> {
    return try {
        HollowResult.Success(block())
    } catch (t: Throwable) {
        val msg = errorMessage ?: t.localizedMessage ?: "Async operation failed"
        Log.e(tag, msg, t)
        HollowResult.Error(t, msg)
    }
}

inline fun <T> HollowResult<T>.onSuccess(action: (T) -> Unit): HollowResult<T> {
    if (this is HollowResult.Success) {
        action(data)
    }
    return this
}

inline fun <T> HollowResult<T>.onError(action: (Throwable, String) -> Unit): HollowResult<T> {
    if (this is HollowResult.Error) {
        action(throwable, message)
    }
    return this
}
