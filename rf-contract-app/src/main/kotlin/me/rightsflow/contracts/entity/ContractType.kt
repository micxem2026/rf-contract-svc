package me.rightsflow.contracts.entity

import jakarta.persistence.*
import org.hibernate.Hibernate

@Entity
@Table(
    name = "LOV_CONTRACT_TYPE",
    uniqueConstraints = [UniqueConstraint(columnNames = ["NAME"])]
)
class ContractType(

    @Id
    @Column(name = "ID")
    val id: Int,

    @Column(name = "NAME", nullable = false, length = 255, unique = true)
    val name: String,

    @Column(name = "DEF", nullable = false)
    val def: Boolean = false

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as ContractType
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}