package ac.sfj.afl3.service

import ac.sfj.afl3.domain.Competition
import ac.sfj.afl3.domain.CompetitionStatus
import ac.sfj.afl3.domain.Match
import ac.sfj.afl3.domain.MatchStatus
import ac.sfj.afl3.domain.ParticipantType
import ac.sfj.afl3.domain.Participant
import ac.sfj.afl3.exception.ApiErrorCode
import ac.sfj.afl3.exception.ResourceNotFoundException
import ac.sfj.afl3.exception.ApiException
import ac.sfj.afl3.repository.CompetitionRepository
import ac.sfj.afl3.repository.MatchRepository
import ac.sfj.afl3.repository.ParticipantRepository
import ac.sfj.afl3.dto.SubmitMatchResultRequest
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

    @Autowired
    private lateinit var participantRepository: ParticipantRepository

    @Test
    fun `submits results into fixed slots and completes final`() {
        val competition = createCompetition("Progression")
        val competitionId = requireNotNull(competition.id)
        val participants = (1..4).map { index ->
            participantRepository.save(Participant(competitionId, "Progression $index"))
        }
        val bracket = matchService.generate(competitionId)

        matchService.submitResult(
            competitionId,
            bracket[0].id,
            SubmitMatchResultRequest(requireNotNull(participants[0].id), 21, 10),
        )
        var final = matchService.findById(competitionId, bracket[2].id)
        assertEquals(requireNotNull(participants[0].id), final.participant1Id)
        assertEquals(MatchStatus.PENDING, final.status)

        matchService.submitResult(
            competitionId,
            bracket[1].id,
            SubmitMatchResultRequest(requireNotNull(participants[3].id), 12, 21),
        )
        final = matchService.findById(competitionId, bracket[2].id)
        assertEquals(requireNotNull(participants[3].id), final.participant2Id)
        assertEquals(MatchStatus.READY, final.status)

        val completedFinal = matchService.submitResult(
            competitionId,
            final.id,
            SubmitMatchResultRequest(requireNotNull(participants[0].id), 21, 18),
        )

        assertEquals(MatchStatus.COMPLETED, completedFinal.status)
        assertEquals(requireNotNull(participants[0].id), completedFinal.winnerId)
        assertEquals(CompetitionStatus.COMPLETED, competitionRepository.findById(competitionId).orElseThrow().status)
    }

    @Test
    fun `rejects result with participant outside the match`() {
        val competition = createCompetition("Invalid winner")
        val competitionId = requireNotNull(competition.id)
        repeat(4) { index ->
            participantRepository.save(Participant(competitionId, "Invalid winner $index"))
        }
        val bracket = matchService.generate(competitionId)

        val exception = assertFailsWith<ApiException> {
            matchService.submitResult(competitionId, bracket[0].id, SubmitMatchResultRequest(Long.MAX_VALUE))
        }

        assertEquals(ApiErrorCode.INVALID_WINNER, exception.code)
    }

    @Test
    fun `rejects result for match that is not ready`() {
        val competition = createCompetition("Not ready")
        val competitionId = requireNotNull(competition.id)
        repeat(4) { index ->
            participantRepository.save(Participant(competitionId, "Not ready $index"))
        }
        val bracket = matchService.generate(competitionId)

        val exception = assertFailsWith<ApiException> {
            matchService.submitResult(competitionId, bracket.last().id, SubmitMatchResultRequest(1))
        }

        assertEquals(ApiErrorCode.MATCH_NOT_READY, exception.code)
    }

    @Test
    fun `generates complete linked bracket and locks competition state`() {
        val competition = createCompetition("Generate")
        val competitionId = requireNotNull(competition.id)
        val participants = (1..4).map { index ->
            participantRepository.save(Participant(competitionId, "Participant $index"))
        }

        val result = matchService.generate(competitionId)

        assertEquals(3, result.size)
        assertEquals(requireNotNull(participants[0].id), result[0].participant1Id)
        assertEquals(requireNotNull(participants[1].id), result[0].participant2Id)
        assertEquals(requireNotNull(participants[2].id), result[1].participant1Id)
        assertEquals(requireNotNull(participants[3].id), result[1].participant2Id)
        assertEquals(listOf(MatchStatus.READY, MatchStatus.READY, MatchStatus.PENDING), result.map { it.status })
        assertEquals(result[2].id, result[0].nextMatchId)
        assertEquals(result[2].id, result[1].nextMatchId)
        assertEquals(CompetitionStatus.IN_MATCH, competitionRepository.findById(competitionId).orElseThrow().status)
    }

    @Test
    fun `rejects generate when participant count is invalid`() {
        val competition = createCompetition("Invalid count")

        val exception = assertFailsWith<ApiException> {
            matchService.generate(requireNotNull(competition.id))
        }

        assertEquals(ApiErrorCode.INVALID_PARTICIPANT_COUNT, exception.code)
    }

    @Test
    fun `rejects generate unless competition is open`() {
        val competition = createCompetition("Already started").apply {
            status = CompetitionStatus.IN_MATCH
        }
        competitionRepository.save(competition)

        val exception = assertFailsWith<ApiException> {
            matchService.generate(requireNotNull(competition.id))
        }

        assertEquals(ApiErrorCode.COMPETITION_NOT_OPEN, exception.code)
    }

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
