package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.ReportJob
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReportJobRepository : JpaRepository<ReportJob, UUID>