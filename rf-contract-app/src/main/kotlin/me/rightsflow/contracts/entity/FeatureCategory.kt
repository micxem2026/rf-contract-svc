package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(name = "SYNC__KLF_FEATURE_CATEGORY")
class FeatureCategory(

    @Id
    @Column(name = "ID", nullable = false)
    val id: Int,

    @Column(name = "NAME", nullable = false, length = 50)
    val name: String

) : BaseAudit() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as FeatureCategory

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}