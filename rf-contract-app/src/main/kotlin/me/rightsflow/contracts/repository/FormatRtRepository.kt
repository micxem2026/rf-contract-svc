package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.FormatRt
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FormatRtRepository : JpaRepository<FormatRt, Long> {

    fun findByIdLicFormat(idLicFormat: Long, pageable: Pageable): Page<FormatRt>
}