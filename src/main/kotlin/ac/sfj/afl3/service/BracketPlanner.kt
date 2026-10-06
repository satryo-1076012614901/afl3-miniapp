package ac.sfj.afl3.service

import ac.sfj.afl3.model.MatchStatus
import org.springframework.stereotype.Component

@Component
class BracketPlanner {

    /**
     * Membentuk topology single-elimination tanpa memaksa jumlah peserta ke power-of-two.
     * Peserta dipasangkan menurut urutan registrasi dan peserta terakhir menerima bye ketika
     * jumlah qualifier ganjil. Jika qualifier terakhir baru menerima bye pada ronde sebelumnya,
     * posisinya ditukar dengan qualifier eligible terdekat agar tidak menerima bye berturut-turut.
     */
    fun create(participantIds: List<Long>): List<PlannedMatch> {
        require(participantIds.size in MIN_PARTICIPANTS..MAX_PARTICIPANTS) {
            "Jumlah peserta harus antara 2 dan 16"
        }

        val drafts = mutableListOf<DraftMatch>()
        var entrants = participantIds.map { Entrant(participantId = it) }
        var round = 1

        while (entrants.size > 1) {
            val arranged = arrangeBye(entrants)
            val roundMatches = arranged.chunked(2).mapIndexed { index, pair ->
                val automaticWinner = pair.singleOrNull()?.participantId
                DraftMatch(
                    round = round,
                    matchNumber = index + 1,
                    participant1Id = pair[0].participantId,
                    participant2Id = pair.getOrNull(1)?.participantId,
                    winnerId = automaticWinner,
                    status = when {
                        automaticWinner != null -> MatchStatus.COMPLETED
                        pair.size == 2 && pair.all { it.participantId != null } -> MatchStatus.READY
                        else -> MatchStatus.PENDING
                    },
                    isBye = pair.size == 1,
                ).also { target ->
                    // Urutan entrant pada pair menentukan slot downstream. Link ini tidak dapat
                    // diturunkan dari ganjil/genap match number pada ronde yang ukurannya tidak genap.
                    pair.forEachIndexed { slotIndex, entrant ->
                        entrant.source?.nextMatch = NextMatchPosition(
                            round = round,
                            matchNumber = index + 1,
                            slot = if (slotIndex == 0) MatchSlot.ONE else MatchSlot.TWO,
                        )
                    }
                }
            }

            drafts += roundMatches
            entrants = roundMatches.map { match ->
                Entrant(
                    participantId = match.winnerId,
                    source = match,
                    receivedBye = match.isBye,
                )
            }
            round += 1
        }

        return drafts.map { it.toPlannedMatch() }
    }

    /**
     * Secara default qualifier terakhir menerima bye. Pertukaran hanya dilakukan ketika qualifier
     * tersebut baru saja menerima bye, sehingga urutan registrasi tetap berubah seminimal mungkin.
     */
    private fun arrangeBye(entrants: List<Entrant>): List<Entrant> {
        if (entrants.size % 2 == 0 || !entrants.last().receivedBye) return entrants

        val replacementIndex = (entrants.lastIndex - 1 downTo 0)
            .first { !entrants[it].receivedBye }
        return entrants.toMutableList().apply {
            val previousLast = this[lastIndex]
            this[lastIndex] = this[replacementIndex]
            this[replacementIndex] = previousLast
        }
    }

    companion object {
        const val MIN_PARTICIPANTS = 2
        const val MAX_PARTICIPANTS = 16
    }
}

private data class Entrant(
    val participantId: Long?,
    val source: DraftMatch? = null,
    val receivedBye: Boolean = false,
)

private data class DraftMatch(
    val round: Int,
    val matchNumber: Int,
    val participant1Id: Long?,
    val participant2Id: Long?,
    val winnerId: Long?,
    val status: MatchStatus,
    val isBye: Boolean,
    var nextMatch: NextMatchPosition? = null,
) {
    fun toPlannedMatch() = PlannedMatch(
        round = round,
        matchNumber = matchNumber,
        participant1Id = participant1Id,
        participant2Id = participant2Id,
        winnerId = winnerId,
        status = status,
        nextMatch = nextMatch,
    )
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
