package ac.sfj.afl3.service

import ac.sfj.afl3.domain.Competition
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
        // Competition menjadi concurrency boundary. Request generate yang datang bersamaan
        // akan diproses bergantian, sehingga hanya request pertama yang melihat status OPEN.
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
        if (participantIds.size !in BracketPlanner.MIN_PARTICIPANTS..BracketPlanner.MAX_PARTICIPANTS) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.INVALID_PARTICIPANT_COUNT,
                "Participant count must be between 2 and 16",
            )
        }

        val plan = bracketPlanner.create(participantIds)
        val matches = matchRepository.saveAllAndFlush(
            plan.map {
                Match(
                    competitionId = competitionId,
                    participant1Id = it.participant1Id,
                    participant2Id = it.participant2Id,
                    winnerId = it.winnerId,
                    round = it.round,
                    matchNumber = it.matchNumber,
                    status = it.status,
                )
            },
        )

        // ID database baru tersedia setelah flush. Peta posisi menghubungkan rencana topology
        // dengan entity yang sudah memiliki ID tanpa bergantung pada query tambahan.
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

        advanceWinner(competition, match, request.winnerId)

        matchRepository.flush()
        return match.toResponse()
    }

    @Transactional
    fun undoResult(competitionId: Long, matchId: Long): MatchResponse {
        val competition = lockCompetition(competitionId)
        val match = requireMatch(competitionId, matchId)
        val isAutomaticBye = listOf(match.participant1Id, match.participant2Id).count { it != null } == 1
        if (match.status != MatchStatus.COMPLETED || isAutomaticBye) {
            throw matchUndoNotAllowed()
        }

        if (match.nextMatchId == null) {
            // Undo final membuka kembali turnamen, tetapi tetap mempertahankan bracket.
            competition.status = CompetitionStatus.IN_MATCH
        } else {
            clearProgression(competitionId, match)
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

        // Status diubah sebelum bulk delete karena delete memakai clearAutomatically.
        // Urutan ini memastikan perubahan Competition sudah di-flush sebelum entity detached.
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

    /**
     * Semua mutasi Match mengunci row Competition yang sama. Dengan begitu perubahan pada
     * beberapa row Match tetap terserialisasi per kompetisi dalam satu transaksi.
     */
    private fun lockCompetition(competitionId: Long) =
        competitionRepository.findByIdForUpdate(competitionId)
            ?: throw ResourceNotFoundException("Competition", competitionId)

    private fun requireMatch(competitionId: Long, matchId: Long) =
        matchRepository.findByIdAndCompetitionId(matchId, competitionId)
            ?: throw ResourceNotFoundException("Match", matchId)

    /**
     * Memajukan winner berdasarkan urutan source yang benar-benar menunjuk target. Cara ini tetap
     * valid untuk ronde ganjil, ketika match 1 dan 3 dapat menuju target yang sama. Target dengan
     * satu source adalah bye tertunda: begitu source selesai, bye ikut selesai dan winner diteruskan.
     */
    private fun advanceWinner(competition: Competition, source: Match, winnerId: Long) {
        val nextMatchId = source.nextMatchId
        if (nextMatchId == null) {
            competition.status = CompetitionStatus.COMPLETED
            return
        }

        val target = requireMatch(source.competitionId, nextMatchId)
        val sources = progressionSources(source.competitionId, nextMatchId)
        setProgressionSlot(target, sources.indexOfSource(source), winnerId)

        if (sources.size == 1) {
            target.winnerId = winnerId
            target.status = MatchStatus.COMPLETED
            advanceWinner(competition, target, winnerId)
        } else if (target.participant1Id != null && target.participant2Id != null) {
            target.status = MatchStatus.READY
        }
    }

    /**
     * Undo melewati node bye otomatis. Node tersebut bukan pertandingan yang dimainkan, sehingga
     * boleh dibersihkan selama pertandingan sesudahnya belum selesai.
     */
    private fun clearProgression(competitionId: Long, source: Match) {
        val target = requireMatch(competitionId, requireNotNull(source.nextMatchId))
        val sources = progressionSources(competitionId, requireNotNull(target.id))

        if (sources.size == 1) {
            target.nextMatchId?.let { downstreamId ->
                val downstream = requireMatch(competitionId, downstreamId)
                if (downstream.status == MatchStatus.COMPLETED) throw matchUndoNotAllowed()
                val byeSources = progressionSources(competitionId, downstreamId)
                setProgressionSlot(downstream, byeSources.indexOfSource(target), null)
                downstream.status = MatchStatus.PENDING
            }
            setProgressionSlot(target, sources.indexOfSource(source), null)
            target.winnerId = null
            target.status = MatchStatus.PENDING
        } else {
            if (target.status == MatchStatus.COMPLETED) throw matchUndoNotAllowed()
            setProgressionSlot(target, sources.indexOfSource(source), null)
            target.status = MatchStatus.PENDING
        }
    }

    private fun progressionSources(competitionId: Long, nextMatchId: Long): List<Match> =
        matchRepository.findAllByCompetitionIdAndNextMatchIdOrderByMatchNumberAsc(
            competitionId,
            nextMatchId,
        )

    private fun List<Match>.indexOfSource(source: Match): Int {
        val index = indexOfFirst { it.id == source.id }
        check(index in 0..1) { "Match source is not linked to its target" }
        return index
    }

    private fun setProgressionSlot(target: Match, slotIndex: Int, participantId: Long?) {
        if (slotIndex == 0) {
            target.participant1Id = participantId
        } else {
            target.participant2Id = participantId
        }
    }

    private fun matchUndoNotAllowed() = ApiException(
        HttpStatus.CONFLICT,
        ApiErrorCode.MATCH_UNDO_NOT_ALLOWED,
        "Match result cannot be undone",
    )
}
