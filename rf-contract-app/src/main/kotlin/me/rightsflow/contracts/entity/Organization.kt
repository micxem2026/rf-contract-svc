package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(
    name = "SYNC__KLF_ORGANIZATION",
    uniqueConstraints = [UniqueConstraint(columnNames = ["GUID"])]
)
class Organization(

    @Id
    @Column(name = "ID")
    val id: Int,

    @Column(name = "GUID", length = 255, unique = true)
    val guid: String? = null,

    @Column(name = "NAME", nullable = false, length = 255)
    val name: String
) : BaseAudit() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Organization
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
