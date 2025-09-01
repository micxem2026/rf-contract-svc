package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.License
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LicenseRepository : JpaRepository<License, Long> {

    @Query(
        """
        select l
        from License l
        where (l.idContract = :idContract) and
              (lower(l.num) like lower(concat('%', :numFilter, '%')) or :numFilter is null)
        """
    )
    fun findByFilter(
        @Param("idContract") idContract: Long,
        @Param("numFilter") numFilter: String?,
        pageable: Pageable
    ): Page<License>
}