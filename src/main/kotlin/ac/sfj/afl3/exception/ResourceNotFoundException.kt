package ac.sfj.afl3.exception

/**
 * Dilempar layer service ketika data yang diminta tidak ada.
 * Dipetakan ke HTTP 404 oleh [GlobalExceptionHandler].
 */
class ResourceNotFoundException(resource: String, id: Any) :
    ApiException(
        status = org.springframework.http.HttpStatus.NOT_FOUND,
        code = when (resource.lowercase()) {
            "competition" -> ApiErrorCode.COMPETITION_NOT_FOUND
            "participant" -> ApiErrorCode.PARTICIPANT_NOT_FOUND
            "match" -> ApiErrorCode.MATCH_NOT_FOUND
            else -> ApiErrorCode.HTTP_ERROR
        },
        message = "$resource dengan id $id tidak ditemukan",
    )
