package me.rightsflow.contracts.service

import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.response.ContractStatusDto
import me.rightsflow.contracts.entity.ContractStatus
import me.rightsflow.contracts.repository.ContractStatusRepository
import org.springframework.stereotype.Service

@Service
class ContractStatusService(
    private val repo: ContractStatusRepository
) {
    fun getById(id: Int): ContractStatusDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, ContractStatus::class.java) }.toDto()

    fun findAll(): List<ContractStatusDto> =
        repo.findAll().sortedBy { it.id }.map { it.toDto() }

    private fun ContractStatus.toDto() = ContractStatusDto(
        id = this.id,
        idContractType = this.idContractType,
        name = this.name,
        def = this.def,
        contractTypeName = this.contractType?.name ?: ""
    )
}