package ac.sfj.afl3.exception

import org.springframework.http.HttpStatus

data class ApiErrorResponse(
    val code: ApiErrorCode,
    val message: String,
)

enum class ApiErrorCode {
    VALIDATION_ERROR,
    INVALID_WINNER,
    MEMBERS_NOT_ALLOWED,
    COMPETITION_NOT_FOUND,
    PARTICIPANT_NOT_FOUND,
    MATCH_NOT_FOUND,
    COMPETITION_NOT_OPEN,
    COMPETITION_NOT_IN_MATCH,
    PARTICIPANT_TYPE_LOCKED,
    DUPLICATE_PARTICIPANT_NAME,
    INVALID_PARTICIPANT_COUNT,
    MATCH_NOT_READY,
    MATCH_UNDO_NOT_ALLOWED,
    DATA_INTEGRITY_VIOLATION,
    HTTP_ERROR,
    INTERNAL_SERVER_ERROR,
}

open class ApiException(
    val status: HttpStatus,
    val code: ApiErrorCode,
    message: String,
) : RuntimeException(message)
