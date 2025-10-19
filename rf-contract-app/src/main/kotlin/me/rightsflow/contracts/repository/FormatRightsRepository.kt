package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.FormatRights
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FormatRightsRepository : JpaRepository<FormatRights, Long> {

    fun findByIdLicFormat(idLicFormat: Long, pageable: Pageable): Page<FormatRights>
}