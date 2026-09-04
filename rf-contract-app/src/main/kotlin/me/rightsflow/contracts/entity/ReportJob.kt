package me.rightsflow.contracts.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

enum class ReportJobStatus { PENDING, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED }

@Entity
@Table(name = "REPORT_JOB")
class ReportJob(
    @Id
    @Column(name = "ID")
    var id: UUID = UUID.randomUUID(),

    @Column(name = "ID_ORG", nullable = false)
    var idOrg: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    var status: ReportJobStatus = ReportJobStatus.PENDING,

    @Column(name = "BACKEND_PID")
    var backendPid: Int? = null,

    @Column(name = "ROW_COUNT")
    var rowCount: Long? = null,

    @Column(name = "ERROR_MESSAGE", columnDefinition = "text")
    var errorMessage: String? = null,

    @Column(name = "CREATED_BY", nullable = false, length = 20)
    var createdBy: String,

    @Column(name = "CREATED_AT", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "STARTED_AT")
    var startedAt: OffsetDateTime? = null,

    @Column(name = "FINISHED_AT")
    var finishedAt: OffsetDateTime? = null
)