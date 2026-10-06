package ac.sfj.afl3.dto

import ac.sfj.afl3.model.Participant
import ac.sfj.afl3.model.TeamMember
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class TeamMemberRequest(
    @field:NotBlank
    val name: String,
)

data class ParticipantRequest(
    @field:NotBlank
    val name: String,

    @field:Valid
    val members: List<TeamMemberRequest>? = null,
)

data class TeamMemberResponse(
    val name: String,
)

data class ParticipantResponse(
    val id: Long,
    val competitionId: Long,
    val name: String,
    val members: List<TeamMemberResponse>?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Participant.toResponse() = ParticipantResponse(
    id = requireNotNull(id),
    competitionId = competitionId,
    name = name,
    members = members?.map {
        TeamMemberResponse(name = it.name)
    },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ParticipantRequest.toTeamMembers(): List<TeamMember>? =
    members?.map {
        TeamMember(name = it.name)
    }