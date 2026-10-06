package ac.sfj.afl3.controller

import ac.sfj.afl3.domain.Competition
import ac.sfj.afl3.domain.CompetitionStatus
import ac.sfj.afl3.domain.Participant
import ac.sfj.afl3.domain.ParticipantType
import ac.sfj.afl3.dto.SubmitMatchResultRequest
import ac.sfj.afl3.exception.ApiErrorCode
import ac.sfj.afl3.exception.ApiException
import ac.sfj.afl3.repository.CompetitionRepository
import ac.sfj.afl3.repository.MatchRepository
import ac.sfj.afl3.repository.ParticipantRepository
import ac.sfj.afl3.service.MatchService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertIs

@SpringBootTest
@AutoConfigureMockMvc
class MatchApiIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var matchService: MatchService

    @Autowired
    private lateinit var competitionRepository: CompetitionRepository

    @Autowired
    private lateinit var participantRepository: ParticipantRepository

    @Autowired
    private lateinit var matchRepository: MatchRepository

    @Test
    fun `match lifecycle exposes documented HTTP statuses`() {
        val setup = createCompetitionWithParticipants("HTTP lifecycle")

        val generated = mockMvc.post("/api/competitions/{competitionId}/matches", setup.competitionId)
            .andExpect {
                status { isCreated() }
                jsonPath("$.length()") { value(3) }
                jsonPath("$[0].participant1Id") { value(setup.participantIds[0]) }
                jsonPath("$[0].status") { value("READY") }
                jsonPath("$[2].status") { value("PENDING") }
            }
            .andReturn()
        val matches = objectMapper.readTree(generated.response.contentAsString)
        val firstMatchId = matches[0]["id"].asLong()

        mockMvc.get("/api/competitions/{competitionId}/matches", setup.competitionId)
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
            }

        mockMvc.get("/api/competitions/{competitionId}/matches/{matchId}", setup.competitionId, firstMatchId)
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(firstMatchId) }
            }

        mockMvc.post(
            "/api/competitions/{competitionId}/matches/{matchId}/result",
            setup.competitionId,
            firstMatchId,
        ) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SubmitMatchResultRequest(setup.participantIds[0], 21, 10))
        }.andExpect {
            status { isOk() }
            jsonPath("$.winnerId") { value(setup.participantIds[0]) }
            jsonPath("$.status") { value("COMPLETED") }
        }

        mockMvc.post(
            "/api/competitions/{competitionId}/matches/{matchId}/undo",
            setup.competitionId,
            firstMatchId,
        ).andExpect {
            status { isOk() }
            jsonPath("$.winnerId") { doesNotExist() }
            jsonPath("$.status") { value("READY") }
        }

        mockMvc.delete("/api/competitions/{competitionId}/matches", setup.competitionId)
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `errors use the agreed code and message body`() {
        mockMvc.get("/api/competitions/{competitionId}/matches", Long.MAX_VALUE)
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("COMPETITION_NOT_FOUND") }
                jsonPath("$.message") { isString() }
                jsonPath("$.length()") { value(2) }
            }

        val setup = createCompetitionWithParticipants("HTTP errors")
        val bracket = matchService.generate(setup.competitionId)

        mockMvc.get(
            "/api/competitions/{competitionId}/matches/{matchId}",
            setup.competitionId,
            Long.MAX_VALUE,
        ).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("MATCH_NOT_FOUND") }
        }

        mockMvc.post(
            "/api/competitions/{competitionId}/matches/{matchId}/result",
            setup.competitionId,
            bracket.first().id,
        ) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"winnerId":${Long.MAX_VALUE}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_WINNER") }
        }

        mockMvc.post(
            "/api/competitions/{competitionId}/matches/{matchId}/result",
            setup.competitionId,
            bracket.first().id,
        ) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"winnerId":${setup.participantIds[0]},"score1":-1}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
            jsonPath("$.message") { isString() }
        }
    }

    @Test
    fun `concurrent bracket generation creates only one bracket`() {
        val setup = createCompetitionWithParticipants("Concurrent generate")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val futures = List(2) {
            executor.submit<Any> {
                ready.countDown()
                start.await()
                runCatching { matchService.generate(setup.competitionId) }.fold(
                    onSuccess = { it },
                    onFailure = { it },
                )
            }
        }

        ready.await()
        start.countDown()
        val outcomes = futures.map { it.get() }
        executor.shutdown()

        assertEquals(1, outcomes.count { it is List<*> })
        val failure = assertIs<ApiException>(outcomes.single { it is Throwable })
        assertEquals(ApiErrorCode.COMPETITION_NOT_OPEN, failure.code)
        assertEquals(3, matchRepository.findAllByCompetitionIdOrderByRoundAscMatchNumberAsc(setup.competitionId).size)
        assertEquals(
            CompetitionStatus.IN_MATCH,
            competitionRepository.findById(setup.competitionId).orElseThrow().status,
        )
    }

    private fun createCompetitionWithParticipants(name: String): Setup {
        val competition = competitionRepository.save(
            Competition(name = name, participantType = ParticipantType.INDIVIDUAL),
        )
        val competitionId = requireNotNull(competition.id)
        val participantIds = (1..4).map { index ->
            requireNotNull(participantRepository.save(Participant(competitionId, "$name $index")).id)
        }
        return Setup(competitionId, participantIds)
    }

    private data class Setup(
        val competitionId: Long,
        val participantIds: List<Long>,
    )
}
