package ac.sfj.afl3.controller

import ac.sfj.afl3.dto.ParticipantRequest
import ac.sfj.afl3.dto.ParticipantResponse
import ac.sfj.afl3.service.ParticipantService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/competitions/{competitionId}/participants")
class ParticipantController(
    private val participantService: ParticipantService,
) {

    @GetMapping
    fun findAll(
        @PathVariable competitionId: Long,
    ): List<ParticipantResponse> =
        participantService.findAll(competitionId)

    @GetMapping("/{participantId}")
    fun findById(
        @PathVariable competitionId: Long,
        @PathVariable participantId: Long,
    ): ParticipantResponse =
        participantService.findById(competitionId, participantId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable competitionId: Long,
        @Valid @RequestBody request: ParticipantRequest,
    ): ParticipantResponse =
        participantService.create(competitionId, request)

    @PutMapping("/{participantId}")
    fun update(
        @PathVariable competitionId: Long,
        @PathVariable participantId: Long,
        @Valid @RequestBody request: ParticipantRequest,
    ): ParticipantResponse =
        participantService.update(competitionId, participantId, request)

    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable competitionId: Long,
        @PathVariable participantId: Long,
    ) {
        participantService.delete(competitionId, participantId)
    }
}