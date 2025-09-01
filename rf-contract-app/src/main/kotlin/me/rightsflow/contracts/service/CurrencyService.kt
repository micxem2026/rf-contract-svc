package me.rightsflow.contracts.service

import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.response.CurrencyDto
import me.rightsflow.contracts.entity.Currency
import me.rightsflow.contracts.repository.CurrencyRepository
import org.springframework.stereotype.Service

@Service
class CurrencyService(
    private val repo: CurrencyRepository
) {
    fun getById(id: Int): CurrencyDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Currency::class.java) }.toDto()

    fun findAll(): List<CurrencyDto> =
        repo.findAll().sortedBy { it.id }.map { it.toDto() }

    private fun Currency.toDto() = CurrencyDto(
        id = this.id,
        isoCharCode = this.isoCharCode,
        name = this.name,
        def = this.def
    )
}