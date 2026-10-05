package ac.sfj.afl3.repository

import ac.sfj.afl3.domain.Competition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CompetitionRepository : JpaRepository<Competition, Long> {

    /** Daftar kompetisi, terbaru lebih dulu. */
    fun findAllByOrderByCreatedAtDescIdDesc(): List<Competition>

    /**
     * `true` bila kompetisi sudah memiliki peserta.
     *
     * Membaca tabel `participant` milik modul Participant (migration V2), sehingga query ini
     * baru dapat dijalankan setelah migration tersebut diterapkan.
     */
    @Query(
        value = "SELECT EXISTS (SELECT 1 FROM participant WHERE competition_id = :competitionId)",
        nativeQuery = true,
    )
    fun hasParticipants(@Param("competitionId") competitionId: Long): Boolean
}
