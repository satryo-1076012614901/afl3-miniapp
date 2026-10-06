package ac.sfj.afl3.repository

import ac.sfj.afl3.model.Match
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MatchRepository : JpaRepository<Match, Long> {
    fun findAllByCompetitionIdOrderByRoundAscMatchNumberAsc(competitionId: Long): List<Match>

    fun findByIdAndCompetitionId(id: Long, competitionId: Long): Match?

    fun findByCompetitionIdAndRoundAndMatchNumber(
        competitionId: Long,
        round: Int,
        matchNumber: Int,
    ): Match?

    fun findAllByCompetitionIdAndNextMatchIdOrderByMatchNumberAsc(
        competitionId: Long,
        nextMatchId: Long,
    ): List<Match>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Match match where match.competitionId = :competitionId")
    fun deleteAllByCompetitionId(@Param("competitionId") competitionId: Long): Int
}
