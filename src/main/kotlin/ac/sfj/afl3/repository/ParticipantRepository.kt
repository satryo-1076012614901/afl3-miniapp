package ac.sfj.afl3.repository

import ac.sfj.afl3.model.Participant
import org.springframework.data.jpa.repository.JpaRepository

interface ParticipantRepository : JpaRepository<Participant, Long> {

    fun findAllByCompetitionIdOrderByCreatedAtAscIdAsc(
        competitionId: Long,
    ): List<Participant>

    fun existsByCompetitionIdAndName(
        competitionId: Long,
        name: String,
    ): Boolean

    fun findByCompetitionIdAndId(
        competitionId: Long,
        id: Long,
    ): Participant?

    fun existsByCompetitionIdAndNameAndIdNot(
        competitionId: Long,
        name: String,
        id: Long,
    ): Boolean
}