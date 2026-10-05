package ac.sfj.afl3.controller

import ac.sfj.afl3.dto.CompetitionRequest
import ac.sfj.afl3.dto.CompetitionResponse
import ac.sfj.afl3.service.CompetitionService
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
@RequestMapping("/api/competitions")
class CompetitionController(private val competitionService: CompetitionService) {

    @GetMapping
    fun findAll(): List<CompetitionResponse> = competitionService.findAll()

    @GetMapping("/{competitionId}")
    fun findById(@PathVariable competitionId: Long): CompetitionResponse =
        competitionService.findById(competitionId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CompetitionRequest): CompetitionResponse =
        competitionService.create(request)

    @PutMapping("/{competitionId}")
    fun update(
        @PathVariable competitionId: Long,
        @Valid @RequestBody request: CompetitionRequest,
    ): CompetitionResponse = competitionService.update(competitionId, request)

    @DeleteMapping("/{competitionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable competitionId: Long) {
        competitionService.delete(competitionId)
    }
}
