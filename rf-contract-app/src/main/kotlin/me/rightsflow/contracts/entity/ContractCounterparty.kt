package me.rightsflow.contracts.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(
    name = "CONTRACT_COUNTERPARTY",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ID_CONTRACT", "ID_CPART"])]
)
class ContractCounterparty (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    var id: Long? = null,

    @Column(name = "ID_CONTRACT", nullable = false)
    var idContract: Long,

    @Column(name = "ID_CPART", nullable = false)
    var idCpart: Int

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CONTRACT", referencedColumnName = "ID", insertable = false, updatable = false)
    var contract: Contract? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CPART", referencedColumnName = "ID", insertable = false, updatable = false)
    var counterparty: Counterparty? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as ContractCounterparty
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}