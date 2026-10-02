package ac.sfj.afl3.service

import ac.sfj.afl3.domain.MatchStatus
import org.springframework.stereotype.Component

@Component
class BracketPlanner {

    fun create(participantIds: List<Long>): List<PlannedMatch> {
        require(participantIds.size in SUPPORTED_PARTICIPANT_COUNTS) {
            "Jumlah peserta harus 4, 8, atau 16"
        }

        val matches = mutableListOf<PlannedMatch>()
        var round = 1
        var matchesInRound = participantIds.size / 2

        while (matchesInRound >= 1) {
            for (matchNumber in 1..matchesInRound) {
                val participantIndex = (matchNumber - 1) * 2
                val isFirstRound = round == 1
                val isFinal = matchesInRound == 1
                matches += PlannedMatch(
                    round = round,
                    matchNumber = matchNumber,
                    participant1Id = participantIds.getOrNull(participantIndex).takeIf { isFirstRound },
                    participant2Id = participantIds.getOrNull(participantIndex + 1).takeIf { isFirstRound },
                    status = if (isFirstRound) MatchStatus.READY else MatchStatus.PENDING,
                    nextMatch = if (isFinal) null else NextMatchPosition(
                        round = round + 1,
                        matchNumber = (matchNumber + 1) / 2,
                        slot = if (matchNumber % 2 == 1) MatchSlot.ONE else MatchSlot.TWO,
                    ),
                )
            }
            round += 1
            matchesInRound /= 2
        }

        return matches
    }

    companion object {
        val SUPPORTED_PARTICIPANT_COUNTS = setOf(4, 8, 16)
    }
}

data class PlannedMatch(
    val round: Int,
    val matchNumber: Int,
    val participant1Id: Long?,
    val participant2Id: Long?,
    val status: MatchStatus,
    val nextMatch: NextMatchPosition?,
)

data class NextMatchPosition(
    val round: Int,
    val matchNumber: Int,
    val slot: MatchSlot,
)

enum class MatchSlot {
    ONE,
    TWO,
}
