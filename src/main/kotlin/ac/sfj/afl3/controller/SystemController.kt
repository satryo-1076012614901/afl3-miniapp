package ac.sfj.afl3.controller

import ac.sfj.afl3.dto.HealthStatus
import ac.sfj.afl3.dto.SystemStatusResponse
import ac.sfj.afl3.service.SystemStatusService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/system")
class SystemController(private val systemStatusService: SystemStatusService) {

    /**
     * Endpoint uji coba: memastikan aplikasi berjalan dan database (RW dan RO) dapat diakses.
     * HTTP 200 bila semua UP, HTTP 503 bila ada yang DOWN.
     */
    @GetMapping("/status")
    fun status(): ResponseEntity<SystemStatusResponse> {
        val response = systemStatusService.getStatus()
        val httpStatus = if (response.status == HealthStatus.UP) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        return ResponseEntity.status(httpStatus).body(response)
    }
}
