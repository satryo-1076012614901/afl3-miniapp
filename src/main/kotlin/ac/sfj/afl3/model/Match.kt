package ac.sfj.afl3.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

@Entity
@Table(name = "match")
class Match(
    @Column(name = "competition_id", nullable = false)
    val competitionId: Long,

    @Column(name = "participant_1_id")
    var participant1Id: Long? = null,

    @Column(name = "participant_2_id")
    var participant2Id: Long? = null,

    @Column(name = "winner_id")
    var winnerId: Long? = null,

    @Column(name = "next_match_id")
    var nextMatchId: Long? = null,

    @Column(nullable = false)
    val round: Int,

    @Column(name = "match_number", nullable = false)
    val matchNumber: Int,

    @Column(name = "score_1")
    var score1: Int? = null,

    @Column(name = "score_2")
    var score2: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MatchStatus,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt

    @PreUpdate
    fun updateTimestamp() {
        updatedAt = now()
    }

    private companion object {
        /**
         * PostgreSQL `timestamptz` menyimpan presisi mikrodetik. Pemotongan ini memastikan timestamp
         * pada response sama persis dengan nilai yang dibaca kembali dari database.
         */
        fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }
}
