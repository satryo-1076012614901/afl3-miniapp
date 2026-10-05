package ac.sfj.afl3.controller

import ac.sfj.afl3.domain.CompetitionStatus
import ac.sfj.afl3.repository.CompetitionRepository
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

/**
 * Test integrasi endpoint Competition terhadap PostgreSQL (lokal atau service CI).
 * Setiap test berjalan dalam transaksi yang di-rollback, sehingga tidak meninggalkan data.
 */
@SpringBootTest
@Transactional
class CompetitionControllerTests {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var competitionRepository: CompetitionRepository

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `create returns 201 with status OPEN`() {
        mockMvc.post("/api/competitions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "  Friday Tournament  ", "participantType": "INDIVIDUAL"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("\$.id") { isNumber() }
            jsonPath("\$.name") { value("Friday Tournament") }
            jsonPath("\$.participantType") { value("INDIVIDUAL") }
            jsonPath("\$.status") { value("OPEN") }
            jsonPath("\$.createdAt") { exists() }
            jsonPath("\$.updatedAt") { exists() }
        }
    }

    @Test
    fun `create with blank name returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/api/competitions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "   ", "participantType": "INDIVIDUAL"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("\$.code") { value("VALIDATION_ERROR") }
        }
    }

    @Test
    fun `create with unknown participant type returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/api/competitions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Friday Tournament", "participantType": "GROUP"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("\$.code") { value("VALIDATION_ERROR") }
        }
    }

    @Test
    fun `list returns newest competition first`() {
        createCompetition("Older Tournament")
        createCompetition("Newer Tournament")

        mockMvc.get("/api/competitions").andExpect {
            status { isOk() }
            jsonPath("\$[0].name") { value("Newer Tournament") }
            jsonPath("\$[1].name") { value("Older Tournament") }
        }
    }

    @Test
    fun `get returns competition detail`() {
        val id = createCompetition("Friday Tournament", "TEAM")

        mockMvc.get("/api/competitions/{competitionId}", id).andExpect {
            status { isOk() }
            jsonPath("\$.id") { value(id) }
            jsonPath("\$.participantType") { value("TEAM") }
        }
    }

    @Test
    fun `get unknown competition returns 404 COMPETITION_NOT_FOUND`() {
        mockMvc.get("/api/competitions/{competitionId}", Long.MAX_VALUE).andExpect {
            status { isNotFound() }
            jsonPath("\$.code") { value("COMPETITION_NOT_FOUND") }
        }
    }

    @Test
    fun `update changes name while OPEN`() {
        val id = createCompetition("Friday Tournament")

        mockMvc.put("/api/competitions/{competitionId}", id) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Friday Tournament Updated", "participantType": "INDIVIDUAL"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("\$.name") { value("Friday Tournament Updated") }
            jsonPath("\$.status") { value("OPEN") }
        }
    }

    @Test
    fun `update when not OPEN returns 409 COMPETITION_NOT_OPEN`() {
        val id = createCompetition("Friday Tournament")
        markInMatch(id)

        mockMvc.put("/api/competitions/{competitionId}", id) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Renamed", "participantType": "INDIVIDUAL"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("\$.code") { value("COMPETITION_NOT_OPEN") }
        }
    }

    @Test
    fun `delete removes competition while OPEN`() {
        val id = createCompetition("Friday Tournament")

        mockMvc.delete("/api/competitions/{competitionId}", id).andExpect {
            status { isNoContent() }
        }
        mockMvc.get("/api/competitions/{competitionId}", id).andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `delete when not OPEN returns 409 COMPETITION_NOT_OPEN`() {
        val id = createCompetition("Friday Tournament")
        markInMatch(id)

        mockMvc.delete("/api/competitions/{competitionId}", id).andExpect {
            status { isConflict() }
            jsonPath("\$.code") { value("COMPETITION_NOT_OPEN") }
        }
    }

    private fun createCompetition(name: String, participantType: String = "INDIVIDUAL"): Long {
        val body = mockMvc.post("/api/competitions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$name", "participantType": "$participantType"}"""
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsString
        return JsonPath.read<Number>(body, "\$.id").toLong()
    }

    /** Mensimulasikan bracket sudah dibuat (perubahan status ini nantinya dilakukan modul Match). */
    private fun markInMatch(id: Long) {
        val competition = requireNotNull(competitionRepository.findByIdOrNull(id))
        competition.status = CompetitionStatus.IN_MATCH
    }
}
