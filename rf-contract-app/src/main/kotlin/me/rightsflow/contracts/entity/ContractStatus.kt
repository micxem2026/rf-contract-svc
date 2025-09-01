package me.rightsflow.contracts.entity

import jakarta.persistence.*
import org.hibernate.Hibernate

@Entity
@Table(
    name = "LOV_CONTRACT_STATUS",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ID_CONTRACT_TYPE", "NAME"])]
)
class ContractStatus(

    @Id
    @Column(name = "ID")
    val id: Int,

    @Column(name = "ID_CONTRACT_TYPE", nullable = false)
    val idContractType: Int,

    @Column(name = "NAME", nullable = false, length = 255)
    val name: String,

    @Column(name = "DEF", nullable = false)
    val def: Boolean = false
) {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CONTRACT_TYPE", referencedColumnName = "ID", insertable = false, updatable = false)
    var contractType: ContractType? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as ContractStatus
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}