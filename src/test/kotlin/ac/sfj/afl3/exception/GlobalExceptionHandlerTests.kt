package ac.sfj.afl3.exception

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals

class GlobalExceptionHandlerTests {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `api exception preserves status code and message`() {
        val exception = ApiException(
            status = HttpStatus.CONFLICT,
            code = ApiErrorCode.MATCH_NOT_READY,
            message = "Match is not ready to receive a result",
        )

        val response = handler.handleApiException(exception)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ApiErrorCode.MATCH_NOT_READY, response.body?.code)
        assertEquals("Match is not ready to receive a result", response.body?.message)
    }

    @Test
    fun `standard bad request uses validation error body`() {
        val response = handler.handleExceptionInternal(
            ex = IllegalArgumentException("invalid"),
            body = null,
            headers = HttpHeaders(),
            statusCode = HttpStatus.BAD_REQUEST,
            request = ServletWebRequest(MockHttpServletRequest()),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response?.statusCode)
        assertEquals(ApiErrorCode.VALIDATION_ERROR, (response?.body as ApiErrorResponse).code)
        assertEquals("Request tidak valid", (response.body as ApiErrorResponse).message)
    }

    @Test
    fun `resource not found maps known resource to stable code`() {
        val response = handler.handleApiException(ResourceNotFoundException("Match", 42))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ApiErrorCode.MATCH_NOT_FOUND, response.body?.code)
        assertEquals("Match dengan id 42 tidak ditemukan", response.body?.message)
    }
}
