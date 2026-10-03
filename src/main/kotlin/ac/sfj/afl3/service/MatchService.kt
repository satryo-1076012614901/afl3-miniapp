package ac.sfj.afl3.service

import ac.sfj.afl3.domain.CompetitionStatus
import ac.sfj.afl3.domain.Match
import ac.sfj.afl3.domain.MatchStatus
import ac.sfj.afl3.dto.MatchResponse
import ac.sfj.afl3.dto.SubmitMatchResultRequest
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

    @Transactional
    fun submitResult(
        competitionId: Long,
        matchId: Long,
        request: SubmitMatchResultRequest,
    ): MatchResponse {
        val competition = lockCompetition(competitionId)
        if (competition.status != CompetitionStatus.IN_MATCH) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.COMPETITION_NOT_IN_MATCH,
                "Competition is not in match",
            )
        }

        val match = requireMatch(competitionId, matchId)
        if (match.status != MatchStatus.READY) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.MATCH_NOT_READY,
                "Match is not ready to receive a result",
            )
        }
        if (request.winnerId != match.participant1Id && request.winnerId != match.participant2Id) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_WINNER,
                "Winner must be one of the match participants",
            )
        }
        if (request.score1?.let { it < 0 } == true || request.score2?.let { it < 0 } == true) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR,
                "Scores must be non-negative",
            )
        }

        match.winnerId = request.winnerId
        match.score1 = request.score1
        match.score2 = request.score2
        match.status = MatchStatus.COMPLETED

        val nextMatchId = match.nextMatchId
        if (nextMatchId == null) {
            competition.status = CompetitionStatus.COMPLETED
        } else {
            val nextMatch = requireMatch(competitionId, nextMatchId)
            if (match.matchNumber % 2 == 1) {
                nextMatch.participant1Id = request.winnerId
            } else {
                nextMatch.participant2Id = request.winnerId
            }
            if (nextMatch.participant1Id != null && nextMatch.participant2Id != null) {
                nextMatch.status = MatchStatus.READY
            }
        }

        matchRepository.flush()
        return match.toResponse()
    }

    @Transactional
    fun undoResult(competitionId: Long, matchId: Long): MatchResponse {
        val competition = lockCompetition(competitionId)
        val match = requireMatch(competitionId, matchId)
        if (match.status != MatchStatus.COMPLETED) {
            throw matchUndoNotAllowed()
        }

        val nextMatchId = match.nextMatchId
        if (nextMatchId == null) {
            competition.status = CompetitionStatus.IN_MATCH
        } else {
            val nextMatch = requireMatch(competitionId, nextMatchId)
            if (nextMatch.status == MatchStatus.COMPLETED) {
                throw matchUndoNotAllowed()
            }
            if (match.matchNumber % 2 == 1) {
                nextMatch.participant1Id = null
            } else {
                nextMatch.participant2Id = null
            }
            nextMatch.status = MatchStatus.PENDING
        }

        match.winnerId = null
        match.score1 = null
        match.score2 = null
        match.status = MatchStatus.READY
        matchRepository.flush()
        return match.toResponse()
    }

    @Transactional
    fun reset(competitionId: Long) {
        val competition = lockCompetition(competitionId)
        if (competition.status != CompetitionStatus.IN_MATCH) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.COMPETITION_NOT_IN_MATCH,
                "Competition is not in match",
            )
        }

        competition.status = CompetitionStatus.OPEN
        matchRepository.deleteAllByCompetitionId(competitionId)
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

    private fun matchUndoNotAllowed() = ApiException(
        HttpStatus.CONFLICT,
        ApiErrorCode.MATCH_UNDO_NOT_ALLOWED,
        "Match result cannot be undone",
    )
}
