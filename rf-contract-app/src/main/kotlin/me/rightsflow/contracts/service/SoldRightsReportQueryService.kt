package me.rightsflow.contracts.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.rightsflow.contracts.dto.response.SoldRightsReportPage
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

@Service
class SoldRightsReportQueryService(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {

    fun getPage(jobId: UUID, cursor: String?, size: Int): SoldRightsReportPage {
        val pageSize = size.coerceIn(1, 2000)
        val (afterDate, afterId, readSoFar) = decodeCursor(cursor)

        data class Row(val contractDate: LocalDate?, val idContract: Long, val payload: String)

        val rowMapper = { rs: java.sql.ResultSet, _: Int ->
            Row(rs.getDate("contract_date")?.toLocalDate(), rs.getLong("id_contract"), rs.getString("payload"))
        }

        val rows = if (afterDate == null) {
            jdbcTemplate.query(
                """
                select id_contract, contract_date, payload::text as payload
                from report_sold_rights_row
                where job_id = ?
                order by contract_date, id_contract
                limit ?
                """.trimIndent(),
                rowMapper, jobId, pageSize
            )
        } else {
            jdbcTemplate.query(
                """
                select id_contract, contract_date, payload::text as payload
                from report_sold_rights_row
                where job_id = ?
                  and (contract_date, id_contract) > (?, ?)
                order by contract_date, id_contract
                limit ?
                """.trimIndent(),
                rowMapper, jobId, afterDate, afterId, pageSize
            )
        }

        val items = rows.map { objectMapper.readTree(it.payload) }
        val newReadSoFar = readSoFar + items.size
        val nextCursor = if (rows.size < pageSize) null
        else rows.last().let { encodeCursor(it.contractDate, it.idContract, newReadSoFar) }

        return SoldRightsReportPage(
            content = items,
            hasMore = nextCursor != null,
            nextCursor = nextCursor,
            readCount = newReadSoFar
        )
    }

    private fun encodeCursor(date: LocalDate?, id: Long, readSoFar: Long): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("${date ?: ""}|$id|$readSoFar".toByteArray())

    private fun decodeCursor(cursor: String?): Triple<LocalDate?, Long?, Long> {
        if (cursor.isNullOrBlank()) return Triple(null, null, 0L)
        val parts = String(Base64.getUrlDecoder().decode(cursor)).split("|")
        val date = parts[0].takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        return Triple(date, parts[1].toLong(), parts[2].toLong())
    }
}