package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.ContractCounterparty
import org.springframework.data.jpa.repository.JpaRepository

interface ContractCounterpartyRepository : JpaRepository<ContractCounterparty, Long> {

    fun findByIdContract(IdContract: Long): List<ContractCounterparty>
    fun findByIdContractIn(idContracts: Collection<Long>): List<ContractCounterparty>
}
