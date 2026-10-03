package ac.sfj.afl3.service

import ac.sfj.afl3.domain.CompetitionStatus
import ac.sfj.afl3.domain.Match
import ac.sfj.afl3.dto.MatchResponse
import ac.sfj.afl3.dto.toResponse
import ac.sfj.afl3.exception.ApiErrorCode
import ac.sfj.afl3.exception.ApiException
import ac.sfj.afl3.exception.ResourceNotFoundException
import ac.sfj.afl3.repository.CompetitionRepository
import ac.sfj.afl3.repository.MatchRepository
import ac.sfj.afl3.repository.ParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MatchService(
    private val competitionRepository: CompetitionRepository,
    private val participantRepository: ParticipantRepository,
    private val matchRepository: MatchRepository,
    private val bracketPlanner: BracketPlanner,
) {
    @Transactional
    fun generate(competitionId: Long): List<MatchResponse> {
        val competition = lockCompetition(competitionId)
        if (competition.status != CompetitionStatus.OPEN) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.COMPETITION_NOT_OPEN,
                "Competition is not open",
            )
        }

        val participantIds = participantRepository
            .findAllByCompetitionIdOrderByCreatedAtAscIdAsc(competitionId)
            .map { requireNotNull(it.id) }
        if (participantIds.size !in BracketPlanner.SUPPORTED_PARTICIPANT_COUNTS) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.INVALID_PARTICIPANT_COUNT,
                "Participant count must be 4, 8, or 16",
            )
        }

        val plan = bracketPlanner.create(participantIds)
        val matches = matchRepository.saveAllAndFlush(
            plan.map {
                Match(
                    competitionId = competitionId,
                    participant1Id = it.participant1Id,
                    participant2Id = it.participant2Id,
                    round = it.round,
                    matchNumber = it.matchNumber,
                    status = it.status,
                )
            },
        )
        val matchesByPosition = matches.associateBy { it.round to it.matchNumber }
        plan.zip(matches).forEach { (planned, match) ->
            val next = planned.nextMatch ?: return@forEach
            match.nextMatchId = requireNotNull(matchesByPosition[next.round to next.matchNumber]?.id)
        }

        competition.status = CompetitionStatus.IN_MATCH
        matchRepository.flush()
        return matches.map { it.toResponse() }
    }

    fun findAll(competitionId: Long): List<MatchResponse> {
        requireCompetition(competitionId)
        return matchRepository.findAllByCompetitionIdOrderByRoundAscMatchNumberAsc(competitionId)
            .map { it.toResponse() }
    }

    fun findById(competitionId: Long, matchId: Long): MatchResponse {
        requireCompetition(competitionId)
        return requireMatch(competitionId, matchId).toResponse()
    }

    private fun requireCompetition(competitionId: Long) =
        competitionRepository.findById(competitionId).orElseThrow {
            ResourceNotFoundException("Competition", competitionId)
        }

    private fun lockCompetition(competitionId: Long) =
        competitionRepository.findByIdForUpdate(competitionId)
            ?: throw ResourceNotFoundException("Competition", competitionId)

    private fun requireMatch(competitionId: Long, matchId: Long) =
        matchRepository.findByIdAndCompetitionId(matchId, competitionId)
            ?: throw ResourceNotFoundException("Match", matchId)
}
