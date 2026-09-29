package ac.sfj.afl3.exception

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Penanganan exception global. Seluruh error dikembalikan dalam format ProblemDetail (RFC 9457).
 *
 * Exception standar Spring MVC (validasi request, method tidak didukung, path tidak ditemukan, dst.)
 * ditangani oleh [ResponseEntityExceptionHandler].
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Data tidak ditemukan")

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ProblemDetail {
        log.warn("Pelanggaran integritas data", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Data bertentangan dengan constraint database")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Exception tidak tertangani", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kesalahan pada server")
    }
}
