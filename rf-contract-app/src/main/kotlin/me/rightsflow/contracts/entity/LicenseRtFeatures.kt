package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(name = "LICENSE_RT_FEATURES")
class LicenseRtFeatures(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "ID_LIC_RT", nullable = false)
    var idLicRt: Long,

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
    @JoinColumn(name = "ID_LIC_RT", referencedColumnName = "ID", insertable = false, updatable = false)
    var licenseRt: LicenseRt? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FEATURE_SET", referencedColumnName = "ID", insertable = false, updatable = false)
    var licenseRtFeatureSet: LicenseRtFeatureSet? = null

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

        other as LicenseRtFeatures

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}