package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.LicenseRights
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface LicenseRightsRepository : JpaRepository<LicenseRights, Long> {

    fun findByLicenseId(licenseId: Long, pageable: Pageable): Page<LicenseRights>
}