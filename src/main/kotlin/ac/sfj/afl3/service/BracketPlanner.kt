package ac.sfj.afl3.service

import ac.sfj.afl3.domain.MatchStatus
import org.springframework.stereotype.Component

@Component
class BracketPlanner {

    /**
     * Membentuk seluruh topology single-elimination tanpa akses database.
     * Urutan participant menjadi urutan seed. Seed awal menerima bye ketika jumlah participant
     * bukan power-of-two; participant lain tetap dipasangkan menurut urutan registrasi.
     */
    fun create(participantIds: List<Long>): List<PlannedMatch> {
        require(participantIds.size in MIN_PARTICIPANTS..MAX_PARTICIPANTS) {
            "Jumlah peserta harus antara 2 dan 16"
        }

        val matches = mutableListOf<PlannedMatch>()
        val bracketSize = participantIds.size.nextPowerOfTwo()
        val byeCount = bracketSize - participantIds.size
        val firstRoundSlots = buildList {
            participantIds.take(byeCount).forEach { participantId ->
                add(participantId to null)
            }
            participantIds.drop(byeCount).chunked(2).forEach { pair ->
                add(pair[0] to pair[1])
            }
        }
        var round = 1
        var matchesInRound = bracketSize / 2

        // Setiap round mempunyai setengah jumlah match dari round sebelumnya.
        // Loop berhenti setelah satu match final ikut dibuat.
        while (matchesInRound >= 1) {
            for (matchNumber in 1..matchesInRound) {
                val isFirstRound = round == 1
                val isFinal = matchesInRound == 1
                val participants = firstRoundSlots.getOrNull(matchNumber - 1).takeIf { isFirstRound }
                val automaticWinner = participants?.first?.takeIf { participants.second == null }
                matches += PlannedMatch(
                    round = round,
                    matchNumber = matchNumber,
                    participant1Id = participants?.first,
                    participant2Id = participants?.second,
                    winnerId = automaticWinner,
                    status = when {
                        automaticWinner != null -> MatchStatus.COMPLETED
                        isFirstRound -> MatchStatus.READY
                        else -> MatchStatus.PENDING
                    },
                    nextMatch = if (isFinal) null else NextMatchPosition(
                        round = round + 1,
                        // Dua match berurutan selalu menuju match yang sama pada round berikutnya.
                        matchNumber = (matchNumber + 1) / 2,
                        // Match ganjil mengisi slot 1; match genap mengisi slot 2.
                        // Slot tetap ini membuat progression dan undo bersifat deterministik.
                        slot = if (matchNumber % 2 == 1) MatchSlot.ONE else MatchSlot.TWO,
                    ),
                )
            }
            round += 1
            matchesInRound /= 2
        }

        // Propagasikan pemenang bye ke slot round berikutnya seperti hasil pertandingan normal.
        val indexByPosition = matches.mapIndexed { index, match ->
            (match.round to match.matchNumber) to index
        }.toMap()
        matches.indices.forEach { index ->
            val source = matches[index]
            val winnerId = source.winnerId ?: return@forEach
            val next = source.nextMatch ?: return@forEach
            val targetIndex = requireNotNull(indexByPosition[next.round to next.matchNumber])
            val target = matches[targetIndex]
            val advanced = when (next.slot) {
                MatchSlot.ONE -> target.copy(participant1Id = winnerId)
                MatchSlot.TWO -> target.copy(participant2Id = winnerId)
            }
            matches[targetIndex] = advanced.copy(
                status = if (advanced.participant1Id != null && advanced.participant2Id != null) {
                    MatchStatus.READY
                } else {
                    MatchStatus.PENDING
                },
            )
        }

        return matches
    }

    companion object {
        const val MIN_PARTICIPANTS = 2
        const val MAX_PARTICIPANTS = 16
    }

    private fun Int.nextPowerOfTwo(): Int = Integer.highestOneBit(this - 1) shl 1
}

data class PlannedMatch(
    val round: Int,
    val matchNumber: Int,
    val participant1Id: Long?,
    val participant2Id: Long?,
    val winnerId: Long? = null,
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
