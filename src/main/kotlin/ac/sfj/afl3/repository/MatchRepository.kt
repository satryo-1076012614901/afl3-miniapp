package ac.sfj.afl3.repository

import ac.sfj.afl3.domain.Match
import org.springframework.data.jpa.repository.JpaRepository

interface MatchRepository : JpaRepository<Match, Long> {
    fun findAllByCompetitionIdOrderByRoundAscMatchNumberAsc(competitionId: Long): List<Match>

    fun findByIdAndCompetitionId(id: Long, competitionId: Long): Match?

    fun findByCompetitionIdAndRoundAndMatchNumber(
        competitionId: Long,
        round: Int,
        matchNumber: Int,
    ): Match?

    fun deleteAllByCompetitionId(competitionId: Long)
}
