package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.LicenseFormat
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LicenseFormatRepository : JpaRepository<LicenseFormat, Long> {

    @Query(
        """
        select c
        from LicenseFormat c
        where (lower(c.name) like lower(concat('%', :nameFilter, '%')) or :nameFilter is null)
        """
    )
    fun findByFilter(
        @Param("nameFilter") nameFilter: String?,
        pageable: Pageable
    ): Page<LicenseFormat>
}