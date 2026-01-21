package me.rightsflow.contracts.entity

import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType
import io.hypersistence.utils.hibernate.type.range.Range
import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "LICENSE", uniqueConstraints = [UniqueConstraint(columnNames = ["GUID"])])
class License(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "ID_CONTRACT", nullable = false)
    var idContract: Long,

    @Column(name = "ID_LIC_FORMAT")
    var idLicFormat: Long? = null,

    @Column(name = "GUID", length = 255, unique = true)
    var guid: String? = null,

    @Column(name = "NUM", nullable = false, length = 255)
    var num: String,

    @Column(name = "NAME", length = 255)
    var name: String? = null,

    @Column(name = "PRICE", nullable = false)
    var price: BigDecimal = BigDecimal.ZERO,

    @Type(PostgreSQLRangeType::class)
    @Column(name = "VALIDITY_PERIOD", nullable = false, columnDefinition = "daterange")
    var validityPeriod: Range<LocalDate> = Range.emptyRange(LocalDate::class.java),

    @Column(name = "DESCRIPTION", length = 511)
    var description: String? = null,

    @Column(name = "VAT_RATE", nullable = false)
    var vatRate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "VAT_AMOUNT", nullable = false)
    var vatAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "TOTAL_AMOUNT", nullable = false)
    var totalAmount: BigDecimal = BigDecimal.ZERO

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LIC_FORMAT", referencedColumnName = "ID", insertable = false, updatable = false)
    var licenseFormat: LicenseFormat? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CONTRACT", referencedColumnName = "ID", insertable = false, updatable = false)
    var contract: Contract? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as License

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}