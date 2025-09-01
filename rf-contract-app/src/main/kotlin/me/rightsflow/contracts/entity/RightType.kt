package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(name = "SYNC__KLF_RIGHT_TYPE")
class RightType(

    @Id
    @Column(name = "ID", nullable = false)
    val id: Int,

    @Column(name = "ID_PARENT")
    val idParent: Int? = null,

    @Column(name = "NAME", nullable = false, length = 255)
    val name: String

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PARENT", referencedColumnName = "ID", insertable = false, updatable = false)
    var parent: RightType? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as RightType

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}