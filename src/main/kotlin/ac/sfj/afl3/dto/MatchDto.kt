package ac.sfj.afl3.dto

import ac.sfj.afl3.domain.Match
import ac.sfj.afl3.domain.MatchStatus
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

data class MatchResponse(
    val id: Long,
    val competitionId: Long,
    val participant1Id: Long?,
    val participant2Id: Long?,
    val winnerId: Long?,
    val nextMatchId: Long?,
    val round: Int,
    val matchNumber: Int,
    val score1: Int?,
    val score2: Int?,
    val status: MatchStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class SubmitMatchResultRequest(
    val winnerId: Long,

    @field:PositiveOrZero
    val score1: Int? = null,

    @field:PositiveOrZero
    val score2: Int? = null,
)

fun Match.toResponse() = MatchResponse(
    id = requireNotNull(id),
    competitionId = competitionId,
    participant1Id = participant1Id,
    participant2Id = participant2Id,
    winnerId = winnerId,
    nextMatchId = nextMatchId,
    round = round,
    matchNumber = matchNumber,
    score1 = score1,
    score2 = score2,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
