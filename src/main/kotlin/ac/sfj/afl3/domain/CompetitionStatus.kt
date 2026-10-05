package ac.sfj.afl3.domain

/**
 * Status kompetisi.
 *
 * - `OPEN`: kompetisi dan pesertanya masih dapat diubah.
 * - `IN_MATCH`: bracket sudah dibuat; diubah oleh modul Match saat generate bracket.
 * - `COMPLETED`: match final sudah selesai; diubah oleh modul Match.
 */
enum class CompetitionStatus {
    OPEN,
    IN_MATCH,
    COMPLETED,
}
