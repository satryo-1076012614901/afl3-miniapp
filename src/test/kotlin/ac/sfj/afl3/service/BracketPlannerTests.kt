package ac.sfj.afl3.service

import ac.sfj.afl3.domain.MatchStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BracketPlannerTests {

    private val planner = BracketPlanner()

    @Test
    fun `creates complete bracket for power of two participant counts`() {
        for (participantCount in listOf(2, 4, 8, 16)) {
            val participants = (1L..participantCount.toLong()).toList()

            val result = planner.create(participants)

            assertEquals(participantCount - 1, result.size)
            assertEquals(participantCount / 2, result.count { it.round == 1 })
            assertEquals(1, result.count { it.nextMatch == null })
            assertEquals(MatchStatus.READY, result.first().status)
            assertEquals(
                if (participantCount == 2) MatchStatus.READY else MatchStatus.PENDING,
                result.last().status,
            )
        }
    }

    @Test
    fun `five participants get two byes without consecutive bye`() {
        val result = planner.create(listOf(10, 20, 30, 40, 50))
        val firstRound = result.filter { it.round == 1 }
        val secondRound = result.filter { it.round == 2 }
        val final = result.single { it.round == 3 }

        assertEquals(6, result.size)
        assertEquals(3, firstRound.size)
        assertEquals(2, secondRound.size)
        assertEquals(2, result.count { it.nextMatch != null && it.participant2Id == null })

        assertEquals(10, firstRound[0].participant1Id)
        assertEquals(20, firstRound[0].participant2Id)
        assertEquals(30, firstRound[1].participant1Id)
        assertEquals(40, firstRound[1].participant2Id)
        assertEquals(50, firstRound[2].winnerId)
        assertEquals(MatchStatus.COMPLETED, firstRound[2].status)

        assertEquals(NextMatchPosition(2, 1, MatchSlot.ONE), firstRound[0].nextMatch)
        assertEquals(NextMatchPosition(2, 2, MatchSlot.ONE), firstRound[1].nextMatch)
        assertEquals(NextMatchPosition(2, 1, MatchSlot.TWO), firstRound[2].nextMatch)
        assertEquals(50, secondRound[0].participant2Id)
        assertEquals(MatchStatus.PENDING, secondRound[0].status)
        assertEquals(MatchStatus.PENDING, secondRound[1].status)
        assertEquals(NextMatchPosition(3, 1, MatchSlot.ONE), secondRound[0].nextMatch)
        assertEquals(NextMatchPosition(3, 1, MatchSlot.TWO), secondRound[1].nextMatch)
        assertEquals(MatchStatus.PENDING, final.status)
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
    fun `brackets from two through sixteen never contain consecutive byes`() {
        for (participantCount in 2..16) {
            val result = planner.create((1L..participantCount.toLong()).toList())
            val incomingCounts = result.mapNotNull { it.nextMatch }
                .groupingBy { it.round to it.matchNumber }
                .eachCount()
            val byPosition = result.associateBy { it.round to it.matchNumber }

            fun PlannedMatch.isBye(): Boolean = if (round == 1) {
                listOf(participant1Id, participant2Id).count { it != null } == 1
            } else {
                incomingCounts[round to matchNumber] == 1
            }

            assertEquals(participantCount - 1, result.count { !it.isBye() })
            result.filter { it.isBye() }.forEach { bye ->
                val target = bye.nextMatch?.let { byPosition[it.round to it.matchNumber] }
                assertFalse(target?.isBye() == true, "participantCount=$participantCount")
            }
        }
    }

    @Test
    fun `rejects participant counts outside MVP`() {
        for (participantCount in listOf(0, 1, 17, 32)) {
            assertFailsWith<IllegalArgumentException> {
                planner.create((1L..participantCount.toLong()).toList())
            }
        }
    }
}
