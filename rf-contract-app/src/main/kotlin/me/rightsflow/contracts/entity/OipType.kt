package me.rightsflow.contracts.entity

import jakarta.persistence.*
import org.hibernate.Hibernate

@Entity
@Table(
    name = "SYNC__LOV_OIP_TYPE",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ID_OIP_SUPER_TYPE", "NAME"])]
)
class OipType(

    @Id
    @Column(name = "ID")
    val id: Int = 0,

    @Column(name = "ID_OIP_SUPER_TYPE", nullable = false)
    val idOipSuperType: Int,

    @Column(name = "NAME", nullable = false, length = 255)
    val name: String
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_OIP_SUPER_TYPE", referencedColumnName = "ID", insertable = false, updatable = false)
    var oipSuperType: OipSuperType? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as OipType

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}