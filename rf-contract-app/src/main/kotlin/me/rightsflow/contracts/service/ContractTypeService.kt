package me.rightsflow.contracts.service

import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.response.ContractTypeDto
import me.rightsflow.contracts.entity.ContractType
import me.rightsflow.contracts.repository.ContractTypeRepository
import org.springframework.stereotype.Service

@Service
class ContractTypeService(
    private val repo: ContractTypeRepository
) {
    fun getById(id: Int): ContractTypeDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, ContractType::class.java) }.toDto()

    fun findAll(): List<ContractTypeDto> =
        repo.findAll().sortedBy { it.id }.map { it.toDto() }

    private fun ContractType.toDto() = ContractTypeDto(
        id = this.id,
        name = this.name,
        def = this.def
    )
}