package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(name = "SYNC__KLF_FEATURE_PLAIN")
class FeaturePlain(

    @Id
    @Column(name = "ID", nullable = false)
    val id: Int,

    @Column(name = "NAME", nullable = false, length = 255)
    val name: String,

    @Column(name = "ID_FEATURE_CATEGORY", nullable = false)
    val idFeatureCategory: Int

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FEATURE_CATEGORY", referencedColumnName = "ID", insertable = false, updatable = false)
    var featureCategory: FeatureCategory? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as FeaturePlain

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}