package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.LicenseOip
import org.springframework.data.jpa.repository.JpaRepository

interface LicenseOipRepository : JpaRepository<LicenseOip, Long> {

    fun findByIdLicense(idLicense: Long): List<LicenseOip>
}