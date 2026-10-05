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
        val competition = getCompetition(competitionId)
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
        val competition = getCompetition(competitionId)
        ensureOpen(competition)
        competitionRepository.delete(competition)
    }

    /**
     * Mengambil kompetisi atau melempar 404 `COMPETITION_NOT_FOUND`.
     * Dapat dipakai service modul lain (Participant, Match) untuk validasi `competitionId` pada path.
     */
    fun getCompetition(competitionId: Long): Competition =
        competitionRepository.findByIdOrNull(competitionId)
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
