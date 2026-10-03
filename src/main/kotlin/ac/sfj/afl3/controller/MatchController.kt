package ac.sfj.afl3.controller

import ac.sfj.afl3.dto.MatchResponse
import ac.sfj.afl3.service.MatchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/competitions/{competitionId}/matches")
class MatchController(private val matchService: MatchService) {

    @GetMapping
    fun findAll(@PathVariable competitionId: Long): List<MatchResponse> =
        matchService.findAll(competitionId)

    @GetMapping("/{matchId}")
    fun findById(
        @PathVariable competitionId: Long,
        @PathVariable matchId: Long,
    ): MatchResponse = matchService.findById(competitionId, matchId)
}
