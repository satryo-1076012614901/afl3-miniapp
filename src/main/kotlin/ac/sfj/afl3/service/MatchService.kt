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

        val nextMatchId = match.nextMatchId
        if (nextMatchId == null) {
            // Match tanpa nextMatch adalah final; winner final menjadi champion secara derivasi.
            competition.status = CompetitionStatus.COMPLETED
        } else {
            val nextMatch = requireMatch(competitionId, nextMatchId)

            // Mapping slot harus sama dengan BracketPlanner: source ganjil ke slot 1,
            // source genap ke slot 2. Slot tidak boleh dipilih berdasarkan urutan submit.
            if (match.matchNumber % 2 == 1) {
                nextMatch.participant1Id = request.winnerId
            } else {
                nextMatch.participant2Id = request.winnerId
            }

            // Match downstream baru boleh dimainkan setelah kedua semifinalis tersedia.
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
        val isAutomaticBye = listOf(match.participant1Id, match.participant2Id).count { it != null } == 1
        if (match.status != MatchStatus.COMPLETED || isAutomaticBye) {
            throw matchUndoNotAllowed()
        }

        val nextMatchId = match.nextMatchId
        if (nextMatchId == null) {
            // Undo final membuka kembali turnamen, tetapi tetap mempertahankan bracket.
            competition.status = CompetitionStatus.IN_MATCH
        } else {
            val nextMatch = requireMatch(competitionId, nextMatchId)

            // Hasil source tidak boleh diubah setelah downstream selesai karena akan
            // menghasilkan jalur pemenang yang tidak konsisten.
            if (nextMatch.status == MatchStatus.COMPLETED) {
                throw matchUndoNotAllowed()
            }

            // Bersihkan slot yang sama dengan slot progression, lalu tahan downstream
            // di PENDING sampai kedua sumbernya kembali mempunyai pemenang.
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

    private fun matchUndoNotAllowed() = ApiException(
        HttpStatus.CONFLICT,
        ApiErrorCode.MATCH_UNDO_NOT_ALLOWED,
        "Match result cannot be undone",
    )
}
