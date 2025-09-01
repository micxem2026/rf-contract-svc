package me.rightsflow.contracts.entity

import jakarta.persistence.*
import org.hibernate.Hibernate

@Entity
@Table(
    name = "SYNC__LOV_OIP_SUPER_TYPE",
    uniqueConstraints = [UniqueConstraint(columnNames = ["NAME"])]
)
class OipSuperType(

    @Id
    @Column(name = "ID")
    val id: Int = 0,

    @Column(name = "NAME", nullable = false, length = 255)
    val name: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as OipSuperType

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}