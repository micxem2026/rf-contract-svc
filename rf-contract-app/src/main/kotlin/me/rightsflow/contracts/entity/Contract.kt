package me.rightsflow.contracts.entity

import com.fasterxml.jackson.annotation.JsonValue
import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType
import io.hypersistence.utils.hibernate.type.range.Range
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import java.math.BigDecimal
import java.time.LocalDate


@Entity
@Table(
    name = "CONTRACT",
    uniqueConstraints = [UniqueConstraint(columnNames = ["GUID"])]
)
class Contract(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    var id: Long? = null,

    @Column(name = "GUID", length = 255, unique = true)
    var guid: String? = null,

    @Column(name = "NUM", nullable = false, length = 255)
    var num: String,

    @Column(name = "ID_ORG", nullable = false)
    var idOrg: Int,

    @Column(name = "ID_ORG_PARTY")
    var idOrgParty: Int? = null,

    @Type(PostgreSQLRangeType::class)
    @Column(name = "VALIDITY_PERIOD", nullable = false, columnDefinition = "daterange")
    var validityPeriod: Range<LocalDate> = Range.emptyRange(LocalDate::class.java),

    @Column(name = "CONTRACT_DATE")
    var contractDate: LocalDate? = null,

    @Column(name = "ID_CONTRACT_TYPE", nullable = false)
    var idContractType: Int,

    @Column(name = "ID_CONTRACT_STATUS", nullable = false)
    var idContractStatus: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "IN_OUT", nullable = false, length = 2)
    var inOut: ContractKind,

    @Column(name = "DESCRIPTION", length = 511)
    var description: String? = null,

    @Column(name = "WARNING", length = 511)
    var warning: String? = null,

    @Column(name = "ID_SIBLING")
    var idSibling: Long? = null,

    @Column(name = "ID_PARENT")
    var idParent: Long? = null,

    @Column(name = "ID_CURRENCY")
    var idCurrency: Int? = null,

    @Column(name = "ID_CURRENCY_PAYMENT")
    var idCurrencyPayment: Int? = null

) : BaseAudit() {

    @Schema(enumAsRef = true)
    enum class ContractKind {
        eS, eP, iS, iP;

        @JsonValue
        override fun toString(): String {
            return this.name
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CURRENCY", referencedColumnName = "ID", insertable = false, updatable = false)
    var currency: Currency? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CURRENCY_PAYMENT", referencedColumnName = "ID", insertable = false, updatable = false)
    var currencyPayment: Currency? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_SIBLING", referencedColumnName = "ID", insertable = false, updatable = false)
    var siblingContract: Contract? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PARENT", referencedColumnName = "ID", insertable = false, updatable = false)
    var parentContract: Contract? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ORG", referencedColumnName = "ID", insertable = false, updatable = false)
    var organization: Organization? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ORG_PARTY", referencedColumnName = "ID", insertable = false, updatable = false)
    var organizationParty: Organization? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CONTRACT_TYPE", referencedColumnName = "ID", insertable = false, updatable = false)
    var contractType: ContractType? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CONTRACT_STATUS", referencedColumnName = "ID", insertable = false, updatable = false)
    var contractStatus: ContractStatus? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Contract
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
