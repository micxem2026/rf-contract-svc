package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@Schema(description = "Контракт")
data class ContractDto(
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "GUID", example = "013-123456789") val guid: String?,
    @field:Schema(description = "Номер контракта", example = "С-0123/2025") val num: String,
    @field:Schema(description = "ID организации", example = "1") val idOrg: Int,
    @field:Schema(description = "Код 1С организации", example = "1") val code1c: String?,
    @field:Schema(description = "Наименование организации", example = "АО 'Рога и Копыта'") val nameOrg: String,
    @field:Schema(description = "ID организации второго участника ВГО-контракта", example = "1") val idOrgParty: Int?,
    @field:Schema(description = "Наименование организации второго участника ВГО-контракта", example = "АО 'Рога и Копыта'") val nameOrgParty: String?,
    @field:Schema(description = "Период действия (начало)", example = "2023-01-01") val validityPeriodStart: LocalDate?,
    @field:Schema(description = "Период действия (конец)", example = "2024-01-01") val validityPeriodEnd: LocalDate?,
    @field:Schema(description = "Дата создания контракта", example = "2023-01-01") val contractDate: LocalDate?,
    @field:Schema(description = "ID типа контракта", example = "1") val idContractType: Int,
    @field:Schema(description = "Наименование типа контракта", example = "Договор") val contractTypeName: String,
    @field:Schema(description = "ID статуса контракта", example = "1") val idContractStatus: Int,
    @field:Schema(description = "Наименование статуса контракта", example = "Подписан") val contractStatusName: String,
    @field:Schema(description = "Статус 1С контракта") val status1c: String?,
    @field:Schema(description = "Вид контракта (внешняя покупка/продажа, внутренняя покупка/продажа)", example = "eS") val inOut: String,
    @field:Schema(description = "Описание", example = "Договор на поставку товаров") val description: String?,
    @field:Schema(description = "Предупреждения целостности контракта", example = "Контракт не содержит лицензий!") val warning: String?,

    @field:Schema(description = "ID родственного контракта для сделки", example = "1") val idSibling: Long?,
    @field:Schema(description = "GUID родственного контракта для сделки", example = "013-123456789") val guidSibling: String?,
    @field:Schema(description = "Номер родственного контракта для сделки", example = "С-0123/2025") val numSibling: String?,

    @field:Schema(description = "ID родительского контракта", example = "1") val idParent: Long?,
    @field:Schema(description = "GUID родительского контракта", example = "013-123456789") val guidParent: String?,
    @field:Schema(description = "Номер родительского контракта", example = "С-0123/2025") val numParent: String?,

    @field:Schema(description = "ID контракта в VP", example = "1") val idContractVp: Int?,

    @field:Schema(description = "ID валюты контракта", example = "1") val idCurrency: Int?,
    @field:Schema(description = "Код валюты контракта", example = "USD") val currencyCode: String?,
    @field:Schema(description = "Название валюты контракта", example = "Доллар США") val currencyName: String?,
    @field:Schema(description = "ID валюты платежа", example = "1") val idCurrencyPayment: Int?,
    @field:Schema(description = "Код валюты платежа", example = "USD") val currencyCodePayment: String?,
    @field:Schema(description = "Название валюты платежа", example = "Доллар США") val currencyNamePayment: String?,

    @field:Schema(description = "Сумма контракта для УНФ", example = "1000.00") val unfContractPrice: BigDecimal?,
    @field:Schema(description = "Сумма НДС контракта для УНФ", example = "200.00") val unfContractVatAmount: BigDecimal?,
    @field:Schema(description = "Итоговая сумма контракта для УНФ", example = "1200.00") val unfContractTotalAmount: BigDecimal?,
    @field:Schema(description = "Ставка НДС контракта для УНФ", example = "22.00") val unfContractVatRate: BigDecimal?,

    @field:Schema(description = "Сумма по лицензиям (цена)", example = "1000.00") val contractPrice: BigDecimal,
    @field:Schema(description = "Сумма НДС по лицензиям", example = "200.00") val contractVatAmount: BigDecimal,
    @field:Schema(description = "Итоговая сумма по лицензиям", example = "1200.00") val contractTotalAmount: BigDecimal,
    @field:Schema(description = "Ставка НДС из лицензий (99 - ставки НДС лицензий разные)", example = "22.00") val contractVatRate: BigDecimal?,

    @field:Schema(description = "Контрагенты договора") val cParties: List<ContractCounterpartyShortDto>,

    @field:Schema(description = "Флаг наличия отсутствующих прав") val missingFlag: Int,

    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime,
    @field:Schema(description = "Пользователь, обновивший запись", example = "admin") val updatedBy: String?,
    @field:Schema(description = "Дата и время обновления записи", example = "2022-01-01T00:00:00Z") val updatedAt: OffsetDateTime?
)

interface ContractWithTotalsProjection {
    // Все поля Contract
    fun getId(): Long
    fun getGuid(): String?
    fun getNum(): String
    fun getIdOrg(): Int
    fun getIdOrgParty(): Int?
    fun getValidityPeriodStart(): LocalDate?
    fun getValidityPeriodEnd(): LocalDate?
    fun getContractDate(): LocalDate?
    fun getIdContractType(): Int
    fun getIdContractStatus(): Int
    fun getStatus1c(): String?
    fun getInOut(): String
    fun getDescription(): String?
    fun getWarning(): String?
    fun getIdSibling(): Long?
    fun getIdParent(): Long?
    fun getIdCurrency(): Int?
    fun getIdCurrencyPayment(): Int?
    fun getIdContractVp(): Int?
    fun getUnfContractPrice(): BigDecimal?
    fun getUnfContractVatAmount(): BigDecimal?
    fun getUnfContractTotalAmount(): BigDecimal?
    fun getUnfContractVatRate(): BigDecimal?
    fun getCreatedBy(): String
    fun getCreatedAt(): Instant
    fun getUpdatedBy(): String?
    fun getUpdatedAt(): Instant?

    // Агрегаты из лицензий
    fun getContractPrice(): BigDecimal?
    fun getContractVatAmount(): BigDecimal?
    fun getContractTotalAmount(): BigDecimal?
    fun getContractVatRate(): BigDecimal?

    // Агрегаты из missing_right
    fun getMissingFlag(): Int

}