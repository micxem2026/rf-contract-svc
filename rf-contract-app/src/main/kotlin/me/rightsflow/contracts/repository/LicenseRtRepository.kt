package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.LicenseRt
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface LicenseRtRepository : JpaRepository<LicenseRt, Long> {

    fun findByLicenseId(licenseId: Long, pageable: Pageable): Page<LicenseRt>
}