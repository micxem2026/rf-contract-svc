package me.rightsflow.contracts.entity

import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType
import io.hypersistence.utils.hibernate.type.range.Range
import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import java.time.LocalDate

@Entity
@Table(name = "SYNC__KLF_FEATURE_TREE")
class FeatureTree(

    @Id
    @Column(name = "ID", nullable = false)
    val id: Int,

    @Column(name = "ID_PARENT")
    val idParent: Int? = null,

    @Column(name = "ID_FEATURE_CATEGORY", nullable = false)
    val idFeatureCategory: Int,

    @Column(name = "ID_FEATURE_PLAIN", nullable = false)
    val idFeaturePlain: Int,

    @Type(PostgreSQLRangeType::class)
    @Column(name = "VALIDITY_PERIOD", nullable = false, columnDefinition = "daterange")
    var validityPeriod: Range<LocalDate> = Range.emptyRange(LocalDate::class.java),

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PARENT", referencedColumnName = "ID", insertable = false, updatable = false)
    var parent: FeatureTree? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FEATURE_CATEGORY", referencedColumnName = "ID", insertable = false, updatable = false)
    var featureCategory: FeatureCategory? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FEATURE_PLAIN", referencedColumnName = "ID", insertable = false, updatable = false)
    var featurePlain: FeaturePlain? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as FeatureTree

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}