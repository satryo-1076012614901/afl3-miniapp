package ac.sfj.afl3

import ac.sfj.afl3.dto.HealthStatus
import ac.sfj.afl3.service.SystemStatusService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
class Afl3ApplicationTests {

    @Autowired
    private lateinit var systemStatusService: SystemStatusService

    @Test
    fun contextLoads() {
    }

    @Test
    fun `system status probes the configured datasource`() {
        val response = systemStatusService.getStatus()

        assertEquals(HealthStatus.UP, response.status)
        assertEquals(HealthStatus.UP, response.database.status)
        assertNotNull(response.database.database)
        assertNotNull(response.database.serverVersion)
    }

}
