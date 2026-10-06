package ac.sfj.afl3.service

import ac.sfj.afl3.model.Competition
import ac.sfj.afl3.model.CompetitionStatus
import ac.sfj.afl3.model.Match
import ac.sfj.afl3.model.MatchStatus
import ac.sfj.afl3.model.ParticipantType
import ac.sfj.afl3.model.Participant
import ac.sfj.afl3.exception.ApiErrorCode
import ac.sfj.afl3.exception.ResourceNotFoundException
import ac.sfj.afl3.exception.ApiException
import ac.sfj.afl3.repository.CompetitionRepository
import ac.sfj.afl3.repository.MatchRepository
import ac.sfj.afl3.repository.ParticipantRepository
import ac.sfj.afl3.dto.SubmitMatchResultRequest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
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

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Test
    fun `undo clears result and fixed downstream slot`() {
        val setup = generatedBracket("Undo")
        matchService.submitResult(
            setup.competitionId,
            setup.matches[0].id,
            SubmitMatchResultRequest(setup.participantIds[0], 21, 10),
        )
        matchService.submitResult(
            setup.competitionId,
            setup.matches[1].id,
            SubmitMatchResultRequest(setup.participantIds[2], 21, 12),
        )

        val undone = matchService.undoResult(setup.competitionId, setup.matches[0].id)
        val final = matchService.findById(setup.competitionId, setup.matches[2].id)

        assertEquals(MatchStatus.READY, undone.status)
        assertEquals(null, undone.winnerId)
        assertEquals(null, undone.score1)
        assertEquals(null, undone.score2)
        assertEquals(null, final.participant1Id)
        assertEquals(setup.participantIds[2], final.participant2Id)
        assertEquals(MatchStatus.PENDING, final.status)
    }

    @Test
    fun `undo rejects rollback when downstream match is completed`() {
        val setup = completedTournament("Undo blocked")

        val exception = assertFailsWith<ApiException> {
            matchService.undoResult(setup.competitionId, setup.matches[0].id)
        }

        assertEquals(ApiErrorCode.MATCH_UNDO_NOT_ALLOWED, exception.code)
    }

    @Test
    fun `undo final reopens competition`() {
        val setup = completedTournament("Undo final")

        val result = matchService.undoResult(setup.competitionId, setup.matches[2].id)

        assertEquals(MatchStatus.READY, result.status)
        assertEquals(null, result.winnerId)
        assertEquals(CompetitionStatus.IN_MATCH, competitionRepository.findById(setup.competitionId).orElseThrow().status)
    }

    @Test
    fun `reset deletes bracket and reopens competition`() {
        val setup = generatedBracket("Reset")

        matchService.reset(setup.competitionId)

        assertTrue(matchService.findAll(setup.competitionId).isEmpty())
        assertEquals(CompetitionStatus.OPEN, competitionRepository.findById(setup.competitionId).orElseThrow().status)
    }

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
    fun `generate and submit responses carry persisted match timestamps`() {
        val setup = generatedBracket("Timestamps")
        val created = setup.matches.first()

        entityManager.flush()
        entityManager.clear()
        val persistedAfterCreate = matchService.findById(setup.competitionId, created.id)
        assertEquals(created.createdAt, persistedAfterCreate.createdAt)
        assertEquals(created.updatedAt, persistedAfterCreate.updatedAt)

        val updated = matchService.submitResult(
            setup.competitionId,
            created.id,
            SubmitMatchResultRequest(setup.participantIds[0], 21, 10),
        )
        entityManager.flush()
        entityManager.clear()
        val persistedAfterUpdate = matchService.findById(setup.competitionId, created.id)
        assertEquals(updated.updatedAt, persistedAfterUpdate.updatedAt)
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
    fun `generates and progresses five participant bracket with only two byes`() {
        val competition = createCompetition("Byes")
        val competitionId = requireNotNull(competition.id)
        val participants = (1..5).map { index ->
            participantRepository.save(Participant(competitionId, "Bye participant $index"))
        }

        val result = matchService.generate(competitionId)
        val firstRound = result.filter { it.round == 1 }
        val secondRound = result.filter { it.round == 2 }

        assertEquals(6, result.size)
        assertEquals(1, firstRound.count { it.status == MatchStatus.COMPLETED })
        assertEquals(requireNotNull(participants[4].id), firstRound[2].winnerId)
        assertEquals(requireNotNull(participants[4].id), secondRound[0].participant2Id)
        assertEquals(MatchStatus.PENDING, secondRound[0].status)
        assertEquals(MatchStatus.PENDING, secondRound[1].status)

        matchService.submitResult(
            competitionId,
            firstRound[0].id,
            SubmitMatchResultRequest(requireNotNull(participants[0].id)),
        )
        matchService.submitResult(
            competitionId,
            firstRound[1].id,
            SubmitMatchResultRequest(requireNotNull(participants[2].id)),
        )

        val progressed = matchService.findAll(competitionId)
        val progressedSecondRound = progressed.filter { it.round == 2 }
        val final = progressed.single { it.round == 3 }
        assertEquals(MatchStatus.READY, progressedSecondRound[0].status)
        assertEquals(MatchStatus.COMPLETED, progressedSecondRound[1].status)
        assertEquals(requireNotNull(participants[2].id), progressedSecondRound[1].winnerId)
        assertEquals(requireNotNull(participants[2].id), final.participant2Id)
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

    private fun generatedBracket(name: String): TournamentSetup {
        val competition = createCompetition(name)
        val competitionId = requireNotNull(competition.id)
        val participantIds = (1..4).map { index ->
            requireNotNull(participantRepository.save(Participant(competitionId, "$name $index")).id)
        }
        return TournamentSetup(competitionId, participantIds, matchService.generate(competitionId))
    }

    private fun completedTournament(name: String): TournamentSetup {
        val setup = generatedBracket(name)
        matchService.submitResult(
            setup.competitionId,
            setup.matches[0].id,
            SubmitMatchResultRequest(setup.participantIds[0]),
        )
        matchService.submitResult(
            setup.competitionId,
            setup.matches[1].id,
            SubmitMatchResultRequest(setup.participantIds[2]),
        )
        matchService.submitResult(
            setup.competitionId,
            setup.matches[2].id,
            SubmitMatchResultRequest(setup.participantIds[0]),
        )
        return setup
    }

    private data class TournamentSetup(
        val competitionId: Long,
        val participantIds: List<Long>,
        val matches: List<ac.sfj.afl3.dto.MatchResponse>,
    )
}
