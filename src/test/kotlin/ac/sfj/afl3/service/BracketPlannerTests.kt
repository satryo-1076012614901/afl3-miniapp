package ac.sfj.afl3.service

import ac.sfj.afl3.domain.MatchStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BracketPlannerTests {

    private val planner = BracketPlanner()

    @Test
    fun `creates complete bracket for every MVP participant count`() {
        for (participantCount in BracketPlanner.SUPPORTED_PARTICIPANT_COUNTS) {
            val participants = (1L..participantCount.toLong()).toList()

            val result = planner.create(participants)

            assertEquals(participantCount - 1, result.size)
            assertEquals(participantCount / 2, result.count { it.round == 1 })
            assertEquals(1, result.count { it.nextMatch == null })
            assertEquals(MatchStatus.READY, result.first().status)
            assertEquals(MatchStatus.PENDING, result.last().status)
        }
    }

    @Test
    fun `pairs participants in registration order and links fixed downstream slots`() {
        val result = planner.create(listOf(10, 20, 30, 40))

        assertEquals(
            PlannedMatch(
                round = 1,
                matchNumber = 1,
                participant1Id = 10,
                participant2Id = 20,
                status = MatchStatus.READY,
                nextMatch = NextMatchPosition(2, 1, MatchSlot.ONE),
            ),
            result[0],
        )
        assertEquals(
            PlannedMatch(
                round = 1,
                matchNumber = 2,
                participant1Id = 30,
                participant2Id = 40,
                status = MatchStatus.READY,
                nextMatch = NextMatchPosition(2, 1, MatchSlot.TWO),
            ),
            result[1],
        )
        assertEquals(2, result[2].round)
        assertEquals(1, result[2].matchNumber)
        assertNull(result[2].participant1Id)
        assertNull(result[2].participant2Id)
        assertNull(result[2].nextMatch)
        assertEquals(MatchStatus.PENDING, result[2].status)
    }

    @Test
    fun `round links do not depend on completion order`() {
        val result = planner.create((1L..8L).toList())
        val firstRound = result.filter { it.round == 1 }

        assertEquals(NextMatchPosition(2, 1, MatchSlot.ONE), firstRound[0].nextMatch)
        assertEquals(NextMatchPosition(2, 1, MatchSlot.TWO), firstRound[1].nextMatch)
        assertEquals(NextMatchPosition(2, 2, MatchSlot.ONE), firstRound[2].nextMatch)
        assertEquals(NextMatchPosition(2, 2, MatchSlot.TWO), firstRound[3].nextMatch)
    }

    @Test
    fun `rejects participant counts outside MVP`() {
        for (participantCount in listOf(0, 2, 3, 5, 6, 7, 9, 32)) {
            assertFailsWith<IllegalArgumentException> {
                planner.create((1L..participantCount.toLong()).toList())
            }
        }
    }
}
