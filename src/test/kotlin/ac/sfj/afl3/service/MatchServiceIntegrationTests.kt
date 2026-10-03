package ac.sfj.afl3.service

import ac.sfj.afl3.domain.Competition
import ac.sfj.afl3.domain.Match
import ac.sfj.afl3.domain.MatchStatus
import ac.sfj.afl3.domain.ParticipantType
import ac.sfj.afl3.exception.ApiErrorCode
import ac.sfj.afl3.exception.ResourceNotFoundException
import ac.sfj.afl3.repository.CompetitionRepository
import ac.sfj.afl3.repository.MatchRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class MatchServiceIntegrationTests {

    @Autowired
    private lateinit var matchService: MatchService

    @Autowired
    private lateinit var competitionRepository: CompetitionRepository

    @Autowired
    private lateinit var matchRepository: MatchRepository

    @Test
    fun `returns empty bracket for existing competition`() {
        val competition = createCompetition("Empty")

        assertTrue(matchService.findAll(requireNotNull(competition.id)).isEmpty())
    }

    @Test
    fun `returns matches ordered by round then match number`() {
        val competition = createCompetition("Ordered")
        val competitionId = requireNotNull(competition.id)
        matchRepository.saveAll(
            listOf(
                Match(competitionId, round = 2, matchNumber = 1, status = MatchStatus.PENDING),
                Match(competitionId, round = 1, matchNumber = 2, status = MatchStatus.READY),
                Match(competitionId, round = 1, matchNumber = 1, status = MatchStatus.READY),
            ),
        )

        val result = matchService.findAll(competitionId)

        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), result.map { it.round to it.matchNumber })
    }

    @Test
    fun `does not expose match from another competition`() {
        val firstCompetition = createCompetition("First")
        val secondCompetition = createCompetition("Second")
        val match = matchRepository.save(
            Match(
                competitionId = requireNotNull(firstCompetition.id),
                round = 1,
                matchNumber = 1,
                status = MatchStatus.READY,
            ),
        )

        val exception = assertFailsWith<ResourceNotFoundException> {
            matchService.findById(requireNotNull(secondCompetition.id), requireNotNull(match.id))
        }

        assertEquals(ApiErrorCode.MATCH_NOT_FOUND, exception.code)
    }

    private fun createCompetition(name: String): Competition =
        competitionRepository.save(
            Competition(name = name, participantType = ParticipantType.INDIVIDUAL),
        )
}
