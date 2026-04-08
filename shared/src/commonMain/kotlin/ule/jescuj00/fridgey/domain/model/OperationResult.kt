package ule.jescuj00.fridgey.domain.model

/**
 * Wrapper for operations that can fail with a typed error code.
 */
sealed class OperationResult<out T> {
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Error(val message: String, val code: ErrorCode) : OperationResult<Nothing>()
}

enum class ErrorCode {
    MAX_COLABORADORES_REACHED,
    UNAUTHORIZED,
    NOT_FOUND,
    INVALID_INPUT,
    DATABASE_ERROR
}
