package ac.sfj.afl3.controller

import ac.sfj.afl3.dto.MatchResponse
import ac.sfj.afl3.dto.SubmitMatchResultRequest
import jakarta.validation.Valid
import ac.sfj.afl3.service.MatchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/api/competitions/{competitionId}/matches")
class MatchController(private val matchService: MatchService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun generate(@PathVariable competitionId: Long): List<MatchResponse> =
        matchService.generate(competitionId)

    @GetMapping
    fun findAll(@PathVariable competitionId: Long): List<MatchResponse> =
        matchService.findAll(competitionId)

    @GetMapping("/{matchId}")
    fun findById(
        @PathVariable competitionId: Long,
        @PathVariable matchId: Long,
    ): MatchResponse = matchService.findById(competitionId, matchId)

    @PostMapping("/{matchId}/result")
    fun submitResult(
        @PathVariable competitionId: Long,
        @PathVariable matchId: Long,
        @Valid @RequestBody request: SubmitMatchResultRequest,
    ): MatchResponse = matchService.submitResult(competitionId, matchId, request)

    @PostMapping("/{matchId}/undo")
    fun undoResult(
        @PathVariable competitionId: Long,
        @PathVariable matchId: Long,
    ): MatchResponse = matchService.undoResult(competitionId, matchId)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@PathVariable competitionId: Long) = matchService.reset(competitionId)
}
