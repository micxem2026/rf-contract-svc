package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.Currency
import org.springframework.data.jpa.repository.JpaRepository

interface CurrencyRepository : JpaRepository<Currency, Int>