package me.rightsflow.contracts.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.Tuple
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.contracts.dto.request.PropertyUpdateBatchRequest
import me.rightsflow.contracts.dto.request.PropertyUpdateRequest
import me.rightsflow.contracts.dto.response.PropertyDataDto
import me.rightsflow.contracts.dto.response.PropertyGroupDto
import me.rightsflow.contracts.dto.response.PropertyValueDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PgeService(
    @PersistenceContext private val entityManager: EntityManager,
    private val objectMapper: ObjectMapper,
    private val subProvider: SecuritySubjectProvider
) {

    /**
     * Получение групп свойств для объекта (pkg_pge.get_property_groups)
     */
    @Transactional(readOnly = true)
    fun getPropertyGroups(objId: Int): List<PropertyGroupDto> {
        val sql = "SELECT * FROM pkg_pge.get_property_groups(:objId)"

        return entityManager.createNativeQuery(sql, Tuple::class.java)
            .setParameter("objId", objId)
            .resultList
            .map { it as Tuple }
            .map { tuple ->
                PropertyGroupDto(
                    id = tuple.get("id", Number::class.java).toInt(),
                    code = tuple.get("code", String::class.java),
                    name = tuple.get("name", String::class.java)
                )
            }
    }

    /**
     * Получение данных по группе свойств для списка сущностей (pkg_pge.get_pg_data)
     */
    @Transactional
    fun getPgData(
        codePg: String,
        entityIds: List<Long>,
        username: String = "admin"
    ): List<PropertyDataDto> {

        val sql = "SELECT * FROM pkg_pge.get_pg_data(:codePg, :entityIds, :username)"

        return entityManager.createNativeQuery(sql, Tuple::class.java)
            .setParameter("codePg", codePg)
            .setParameter("entityIds", entityIds.toTypedArray())
            .setParameter("username", username)
            .resultList
            .map { it as Tuple }
            .map { mapTupleToPropertyData(it) }
    }

    /**
     * Получение конкретного свойства (pkg_pge.get_property)
     */
    @Transactional
    fun getProperty(
        codePg: String,
        property: String, // code или id свойства строкой
        entityIds: List<Long>,
        username: String = "admin"
    ): List<PropertyDataDto> {
        val sql = "SELECT * FROM pkg_pge.get_property(:codePg, :property, :entityIds, :username)"

        return entityManager.createNativeQuery(sql, Tuple::class.java)
            .setParameter("codePg", codePg)
            .setParameter("property", property)
            .setParameter("entityIds", entityIds.toTypedArray())
            .setParameter("username", username)
            .resultList
            .map { it as Tuple }
            .map { mapTupleToPropertyData(it) }
    }

    /**
     * Обновление значения свойства (pkg_pge.update_property)
     */
    @Transactional
    fun updateProperty(
        pod: PropertyUpdateRequest
    ): PropertyDataDto {
        val sql = "SELECT * FROM pkg_pge.update_property(:codePg, :property, :idEntity, :value, :username)"

        val tuple = entityManager.createNativeQuery(sql, Tuple::class.java)
            .setParameter("codePg", pod.codePg)
            .setParameter("property", pod.property)
            .setParameter("idEntity", pod.idEntity)
            .setParameter("value", if (pod.value == "") null else pod.value)
            .setParameter("username", subProvider.currentSub())
            .singleResult as Tuple

        return mapTupleToPropertyData(tuple)
    }

    /**
     * Пакетное обновление свойств (pkg_pge.update_properties_batch)
     */
    @Transactional
    fun updatePropertiesBatch(
        pod: PropertyUpdateBatchRequest
    ): Int {
        // Сериализуем список DTO в JSON строку, которую ожидает функция
        val jsonUpdates = objectMapper.writeValueAsString(pod.updates)

        val sql = "SELECT pkg_pge.update_properties_batch(cast(:updates as text), :username)"

        val result = entityManager.createNativeQuery(sql)
            .setParameter("updates", jsonUpdates)
            .setParameter("username", subProvider.currentSub())
            .singleResult

        return (result as Number).toInt()
    }

    /**
     * Очистка мусора (pkg_pge.garbage_pge_data)
     */
    @Transactional
    fun garbagePgeData(): Int {
        val sql = "SELECT pkg_pge.garbage_pge_data()"

        val result = entityManager.createNativeQuery(sql)
            .singleResult

        return (result as Number).toInt()
    }

    private fun mapTupleToPropertyData(tuple: Tuple): PropertyDataDto {
        val propertyValue = tuple.get("property_value", String::class.java)
        val displayValue = tuple.get("display_value", String::class.java)
        return PropertyDataDto(
            idEntity = tuple.get("id_entity", Number::class.java).toLong(),
            //id = tuple.get("id", Number::class.java).toInt(),
            //idPgl = tuple.get("id_pgl", Number::class.java).toInt(),
            idProperty = tuple.get("id_property", Number::class.java).toInt(),
            pgOrder = tuple.get("pg_order", Number::class.java).toInt(),
            nameProp = tuple.get("name_prop", String::class.java),
            codeProp = tuple.get("code_prop", String::class.java),
            idPropType = tuple.get("id_prop_type", Number::class.java).toInt(),
            namePropType = tuple.get("name_prop_type", String::class.java),
            useMultiSelect = tuple.get("use_multi_select", Boolean::class.javaObjectType),
            value = if (propertyValue != null && displayValue != null) {
                propertyValue.removeSurrounding("{", "}")
                             .split(",").zip(displayValue.split("||")) { propValue, dispValue ->
                    PropertyValueDto(propValue, if (dispValue == "{}") "" else dispValue)
                }
            } else {
                listOf(PropertyValueDto(null, ""))
            }
        )
    }
}
