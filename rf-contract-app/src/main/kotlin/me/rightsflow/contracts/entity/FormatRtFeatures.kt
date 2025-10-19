package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(name = "FORMAT_RT_FEATURES")
class FormatRtFeatures(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "ID_FMT_RIGHTS", nullable = false)
    var idFmtRights: Long,

    @Column(name = "ID_FEATURE_SET", nullable = false)
    var idFeatureSet: Long,

    @Column(name = "ID_FEATURE_CATEGORY", nullable = false)
    var idFeatureCategory: Int,

    @Column(name = "ID_FEATURE", nullable = false)
    var idFeature: Int,

    @Column(name = "IS_INCLUDED", nullable = false)
    var isIncluded: Boolean = true,

    @Column(name = "IS_NATIVE", nullable = false)
    var isNative: Boolean = false

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FMT_RIGHTS", referencedColumnName = "ID", insertable = false, updatable = false)
    var fmtRights: FormatRights? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FEATURE_SET", referencedColumnName = "ID", insertable = false, updatable = false)
    var formatRtFeatureSet: FormatRtFeatureSet? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FEATURE_CATEGORY", referencedColumnName = "ID", insertable = false, updatable = false)
    var featureCategory: FeatureCategory? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FEATURE", referencedColumnName = "ID", insertable = false, updatable = false)
    var featureTree: FeatureTree? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as FormatRtFeatures

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}