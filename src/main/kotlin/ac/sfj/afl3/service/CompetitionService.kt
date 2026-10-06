package ac.sfj.afl3.service

import ac.sfj.afl3.domain.Competition
import ac.sfj.afl3.domain.CompetitionStatus
import ac.sfj.afl3.dto.CompetitionRequest
import ac.sfj.afl3.dto.CompetitionResponse
import ac.sfj.afl3.dto.toResponse
import ac.sfj.afl3.exception.ApiErrorCode
import ac.sfj.afl3.exception.ApiException
import ac.sfj.afl3.exception.ResourceNotFoundException
import ac.sfj.afl3.repository.CompetitionRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CompetitionService(private val competitionRepository: CompetitionRepository) {

    fun findAll(): List<CompetitionResponse> =
        competitionRepository.findAllByOrderByCreatedAtDescIdDesc().map { it.toResponse() }

    fun findById(competitionId: Long): CompetitionResponse =
        getCompetition(competitionId).toResponse()

    /** Aturan 1: kompetisi baru selalu berstatus `OPEN`. */
    @Transactional
    fun create(request: CompetitionRequest): CompetitionResponse =
        competitionRepository.save(
            Competition(name = request.name.trim(), participantType = request.participantType),
        ).toResponse()

    /**
     * Mengubah kompetisi saat `OPEN`.
     * Aturan 2: `participantType` tidak boleh diubah setelah kompetisi memiliki peserta.
     */
    @Transactional
    fun update(competitionId: Long, request: CompetitionRequest): CompetitionResponse {
        val competition = getCompetitionForUpdate(competitionId)
        ensureOpen(competition)

        if (request.participantType != competition.participantType &&
            competitionRepository.hasParticipants(competitionId)
        ) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.PARTICIPANT_TYPE_LOCKED,
                "participantType tidak dapat diubah karena kompetisi sudah memiliki peserta",
            )
        }

        competition.name = request.name.trim()
        competition.participantType = request.participantType
        // Flush agar @PreUpdate memperbarui updatedAt sebelum response dibentuk.
        return competitionRepository.saveAndFlush(competition).toResponse()
    }

    /** Menghapus kompetisi saat `OPEN`; peserta ikut terhapus melalui `ON DELETE CASCADE`. */
    @Transactional
    fun delete(competitionId: Long) {
        val competition = getCompetitionForUpdate(competitionId)
        ensureOpen(competition)
        competitionRepository.delete(competition)
    }

    /**
     * Mengambil kompetisi atau melempar 404 `COMPETITION_NOT_FOUND`.
     * Dipakai untuk operasi baca dan validasi `competitionId` pada path. Operasi yang mengubah
     * kompetisi atau isinya memakai [getCompetitionForUpdate].
     */
    fun getCompetition(competitionId: Long): Competition =
        competitionRepository.findByIdOrNull(competitionId)
            ?: throw ResourceNotFoundException("Competition", competitionId)

    /**
     * Mengambil kompetisi dengan row lock (`SELECT ... FOR UPDATE`) atau melempar 404 `COMPETITION_NOT_FOUND`.
     *
     * Dipakai oleh setiap operasi yang mengubah kompetisi atau isinya: update/delete kompetisi serta
     * create/update/delete peserta. Modul Match mengunci row yang sama untuk generate, submit, undo,
     * dan reset, sehingga seluruh operasi tersebut berjalan bergantian per kompetisi dan pemeriksaan
     * status (mis. [ensureOpen]) selalu membaca status terbaru. Tanpa lock, peserta yang ditambahkan
     * bersamaan dengan generate dapat masuk ke kompetisi `IN_MATCH` tanpa tercantum di bracket.
     *
     * Wajib dipanggil di dalam transaksi tulis milik pemanggil (`@Transactional` tanpa `readOnly`).
     * Lock dilepas saat transaksi pemanggil selesai.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun getCompetitionForUpdate(competitionId: Long): Competition =
        competitionRepository.findByIdForUpdate(competitionId)
            ?: throw ResourceNotFoundException("Competition", competitionId)

    /**
     * Melempar 409 `COMPETITION_NOT_OPEN` bila kompetisi tidak berstatus `OPEN`.
     * Dapat dipakai modul Participant sebelum membuat, mengubah, atau menghapus peserta.
     */
    fun ensureOpen(competition: Competition) {
        if (competition.status != CompetitionStatus.OPEN) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.COMPETITION_NOT_OPEN,
                "Kompetisi tidak berstatus OPEN",
            )
        }
    }
}
