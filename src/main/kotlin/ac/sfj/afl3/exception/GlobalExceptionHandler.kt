package ac.sfj.afl3.exception

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.context.request.WebRequest

/**
 * Penanganan exception global. Seluruh error memakai body `code` dan `message`.
 *
 * Exception standar Spring MVC (validasi request, method tidak didukung, path tidak ditemukan, dst.)
 * ditangani oleh [ResponseEntityExceptionHandler].
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(ex.status).body(ApiErrorResponse(ex.code, ex.message ?: ex.code.name))

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ApiErrorResponse> {
        log.warn("Pelanggaran integritas data", ex)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                ApiErrorCode.DATA_INTEGRITY_VIOLATION,
                "Data bertentangan dengan constraint database",
            ),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiErrorResponse> {
        log.error("Exception tidak tertangani", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse(ApiErrorCode.INTERNAL_SERVER_ERROR, "Terjadi kesalahan pada server"),
        )
    }

    public override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val isValidationError = statusCode.value() == HttpStatus.BAD_REQUEST.value()
        val error = ApiErrorResponse(
            code = if (isValidationError) ApiErrorCode.VALIDATION_ERROR else ApiErrorCode.HTTP_ERROR,
            message = if (isValidationError) "Request tidak valid" else "Request tidak dapat diproses",
        )
        return ResponseEntity(error, headers, statusCode)
    }
}
