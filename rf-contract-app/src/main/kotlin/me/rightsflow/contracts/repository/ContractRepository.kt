package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.Contract
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContractRepository : JpaRepository<Contract, Long> {

    @Query(
        """
        select c
        from Contract c
        where (c.idContractType = :idType or :idType is null)
          and (c.idContractStatus = :idStatus or :idStatus is null)
          and (c.idOrg = :idOrg or :idOrg is null)
          and (c.inOut = :inOut or :inOut is null)
          and (lower(c.num) like lower(concat('%', :numFilter, '%')) or :numFilter is null)
        """
    )
    fun findByFilter(
        @Param("idType") idType: Int?,
        @Param("idStatus") idStatus: Int?,
        @Param("idOrg") idOrg: Int?,
        @Param("numFilter") numFilter: String?,
        @Param("inOut") inOut: Contract.ContractKind?,
        pageable: Pageable
    ): Page<Contract>
}
