package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service

@Service
class ConstraintService(
    @PersistenceContext private val em: EntityManager
) {

    fun checkOipUse(idOip: Int): Boolean {
        return em.createNativeQuery("select pkg_constraint.check_oip_use(:p_id_oip)")
            .setParameter("p_id_oip", idOip)
            .singleResult as Boolean
    }

    fun checkRightTypeUse(idRightType: Int): Boolean {
        return em.createNativeQuery("select pkg_constraint.check_right_type_use(:p_id_right_type)")
            .setParameter("p_id_right_type", idRightType)
            .singleResult as Boolean
    }

    fun checkFeatureCategoryUse(idFeatureCategory: Int): Boolean {
        return em.createNativeQuery("select pkg_constraint.check_feature_category_use(:p_id_feature_category)")
            .setParameter("p_id_feature_category", idFeatureCategory)
            .singleResult as Boolean
    }

    fun checkFeatureUse(idFeature: Int): Boolean {
        return em.createNativeQuery("select pkg_constraint.check_feature_use(:p_id_feature)")
            .setParameter("p_id_feature", idFeature)
            .singleResult as Boolean
    }

    fun checkCounterpartyUse(idCounterparty: Int): Boolean {
        return em.createNativeQuery("select pkg_constraint.check_counterparty_use(:p_id_counterparty)")
            .setParameter("p_id_counterparty", idCounterparty)
            .singleResult as Boolean
    }

    fun checkOrganizationUse(idOrganization: Int): Boolean {
        return em.createNativeQuery("select pkg_constraint.check_organization_use(:p_id_organization)")
            .setParameter("p_id_organization", idOrganization)
            .singleResult as Boolean
    }
}