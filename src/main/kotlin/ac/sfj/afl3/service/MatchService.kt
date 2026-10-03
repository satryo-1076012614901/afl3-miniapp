package ac.sfj.afl3.service

import ac.sfj.afl3.dto.MatchResponse
import ac.sfj.afl3.dto.toResponse
import ac.sfj.afl3.exception.ResourceNotFoundException
import ac.sfj.afl3.repository.CompetitionRepository
import ac.sfj.afl3.repository.MatchRepository
import ac.sfj.afl3.repository.ParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MatchService(
    private val competitionRepository: CompetitionRepository,
    private val participantRepository: ParticipantRepository,
    private val matchRepository: MatchRepository,
    private val bracketPlanner: BracketPlanner,
) {
    fun findAll(competitionId: Long): List<MatchResponse> {
        requireCompetition(competitionId)
        return matchRepository.findAllByCompetitionIdOrderByRoundAscMatchNumberAsc(competitionId)
            .map { it.toResponse() }
    }

    fun findById(competitionId: Long, matchId: Long): MatchResponse {
        requireCompetition(competitionId)
        return requireMatch(competitionId, matchId).toResponse()
    }

    private fun requireCompetition(competitionId: Long) =
        competitionRepository.findById(competitionId).orElseThrow {
            ResourceNotFoundException("Competition", competitionId)
        }

    private fun requireMatch(competitionId: Long, matchId: Long) =
        matchRepository.findByIdAndCompetitionId(matchId, competitionId)
            ?: throw ResourceNotFoundException("Match", matchId)
}
