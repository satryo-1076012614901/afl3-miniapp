package ac.sfj.afl3.dto

import ac.sfj.afl3.model.Competition
import ac.sfj.afl3.model.CompetitionStatus
import ac.sfj.afl3.model.ParticipantType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/** Request membuat atau mengubah kompetisi. */
data class CompetitionRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    val participantType: ParticipantType,
)

data class CompetitionResponse(
    val id: Long,
    val name: String,
    val participantType: ParticipantType,
    val status: CompetitionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Competition.toResponse() = CompetitionResponse(
    id = requireNotNull(id),
    name = name,
    participantType = participantType,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
