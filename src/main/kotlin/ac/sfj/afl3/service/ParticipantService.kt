package ac.sfj.afl3.service

import ac.sfj.afl3.domain.Participant
import ac.sfj.afl3.domain.ParticipantType
import ac.sfj.afl3.dto.ParticipantRequest
import ac.sfj.afl3.dto.ParticipantResponse
import ac.sfj.afl3.dto.toResponse
import ac.sfj.afl3.dto.toTeamMembers
import ac.sfj.afl3.exception.ApiErrorCode
import ac.sfj.afl3.exception.ApiException
import ac.sfj.afl3.exception.ResourceNotFoundException
import ac.sfj.afl3.repository.ParticipantRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ParticipantService(
    private val participantRepository: ParticipantRepository,
    private val competitionService: CompetitionService,
) {

    fun findAll(competitionId: Long): List<ParticipantResponse> {
        competitionService.getCompetition(competitionId)

        return participantRepository
            .findAllByCompetitionIdOrderByCreatedAtAscIdAsc(competitionId)
            .map { it.toResponse() }
    }

    fun findById(
        competitionId: Long,
        participantId: Long,
    ): ParticipantResponse {
        competitionService.getCompetition(competitionId)

        return getParticipant(competitionId, participantId).toResponse()
    }

    @Transactional
    fun create(
        competitionId: Long,
        request: ParticipantRequest,
    ): ParticipantResponse {
        val competition = competitionService.getCompetition(competitionId)
        competitionService.ensureOpen(competition)

        validateMembers(competition.participantType, request)

        val name = request.name.trim()

        if (participantRepository.existsByCompetitionIdAndName(competitionId, name)) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.DUPLICATE_PARTICIPANT_NAME,
                "Nama participant sudah digunakan dalam kompetisi ini",
            )
        }

        return participantRepository.save(
            Participant(
                competitionId = competitionId,
                name = name,
                members = request.toTeamMembers(),
            ),
        ).toResponse()
    }

    @Transactional
    fun update(
        competitionId: Long,
        participantId: Long,
        request: ParticipantRequest,
    ): ParticipantResponse {
        val competition = competitionService.getCompetition(competitionId)
        competitionService.ensureOpen(competition)

        val participant = getParticipant(competitionId, participantId)

        validateMembers(competition.participantType, request)

        val name = request.name.trim()

        if (
            participantRepository.existsByCompetitionIdAndNameAndIdNot(
                competitionId,
                name,
                participantId,
            )
        ) {
            throw ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.DUPLICATE_PARTICIPANT_NAME,
                "Nama participant sudah digunakan dalam kompetisi ini",
            )
        }

        participant.name = name
        participant.members = request.toTeamMembers()

        return participantRepository.saveAndFlush(participant).toResponse()
    }

    @Transactional
    fun delete(
        competitionId: Long,
        participantId: Long,
    ) {
        val competition = competitionService.getCompetition(competitionId)
        competitionService.ensureOpen(competition)

        val participant = getParticipant(competitionId, participantId)
        participantRepository.delete(participant)
    }

    private fun getParticipant(
        competitionId: Long,
        participantId: Long,
    ): Participant =
        participantRepository.findByCompetitionIdAndId(
            competitionId,
            participantId,
        ) ?: throw ResourceNotFoundException("Participant", participantId)

    private fun validateMembers(
        participantType: ParticipantType,
        request: ParticipantRequest,
    ) {
        if (
            participantType == ParticipantType.INDIVIDUAL &&
            request.members != null
        ) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.MEMBERS_NOT_ALLOWED,
                "members hanya boleh diisi untuk kompetisi TEAM",
            )
        }
    }
}