package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(
    name = "SYNC__KLF_COUNTERPARTY",
    uniqueConstraints = [UniqueConstraint(columnNames = ["GUID"])]
)
class Counterparty(

    @Id
    @Column(name = "ID")
    val id: Int,

    @Column(name = "GUID", length = 255, unique = true)
    val guid: String? = null,

    @Column(name = "CODE_1C", length = 50, unique = true)
    val code_1c: String? = null,

    @Column(name = "NAME", nullable = false, length = 255)
    val name: String,

    @Column(name = "ID_ORG_REF")
    val idOrgRef: Int? = null,
) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ORG_REF", referencedColumnName = "ID", insertable = false, updatable = false)
    var orgRef: Organization? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Counterparty
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
