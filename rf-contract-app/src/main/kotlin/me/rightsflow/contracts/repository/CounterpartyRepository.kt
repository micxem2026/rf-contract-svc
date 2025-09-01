package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.Counterparty
import org.springframework.data.jpa.repository.JpaRepository

interface CounterpartyRepository : JpaRepository<Counterparty, Int>
