package ac.sfj.afl3.domain

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
@Table(name = "competition")
class Competition(
    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false)
    var participantType: ParticipantType,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CompetitionStatus = CompetitionStatus.OPEN

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
         * PostgreSQL `timestamptz` hanya menyimpan presisi mikrodetik. Waktu dipotong ke mikrodetik
         * agar nilai pada response create/update sama persis dengan nilai yang tersimpan dan dibaca ulang.
         */
        fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }
}
