package me.rightsflow.intersync.dto

import java.time.Instant

// SYNC__USERS
data class UsersAvroMessage(
    val id: Int?,
    val username: String,
    val display_name: String,
    val email: String,
    val password_hash: String,
    val enabled: Boolean?,
    val account_non_expired: Boolean?,
    val account_non_locked: Boolean?,
    val expiration_date: Long?,
    val last_logon: Long?,
    val created_at: Long?,
    val updated_at: Long?,
    val user_type: String
)


// SYNC__KLF_COUNTERPARTY
data class KlfCounterpartyAvroMessage(
    val id: Int?,
    val guid: String?,
    val name: String,
    val id_org_ref: Int?,
    val created_by: String,
    val created_at: Instant?,
    val updated_by: String?,
    val updated_at: Instant?
)

// SYNC__KLF_ORGANIZATION
data class KlfOrganizationAvroMessage(
    val id: Int?,
    val guid: String?,
    val name: String,
    val created_by: String,
    val created_at: Instant?,
    val updated_by: String?,
    val updated_at: Instant?
)

// SYNC__LOV_OIP_SUPER_TYPE
data class LovOipSuperTypeAvroMessage(
    val id: Int?,
    val name: String
)

// SYNC__LOV_OIP_TYPE
data class LovOipTypeAvroMessage(
    val id: Int?,
    val id_oip_super_type: Int?,
    val name: String
)

// SYNC__KLF_OIP
data class KlfOipAvroMessage(
    val id: Int?,
    val guid: String?,
    val id_oip_super_type: Int?,
    val id_oip_type: Int?,
    val name: String,
    val part_num: Int?,
    val part_count: Int?,
    val duration: Long?, //io.debezium.time.MicroDuration
    val description: String?,
    val has_parent: Boolean?,
    val has_children: Boolean?,
    val children_count: Int?,
    val root_id: Int?,
    val native_name: String?,
    val full_name: String?,
    val release_year: String?,
    val created_by: String,
    val created_at: Instant?,
    val updated_by: String?,
    val updated_at: Instant?
)

// SYNC__KLF_RIGHT_TYPE
data class KlfRightTypeAvroMessage(
    val id: Int?,
    val id_parent: Int?,
    val name: String,
    val description: String?,
    val created_by: String,
    val created_at: Instant?,
    val updated_by: String?,
    val updated_at: Instant?
)

// SYNC__KLF_FEATURE_CATEGORY
data class KlfFeatureCategoryAvroMessage(
    val id: Int?,
    val name: String,
    val created_by: String,
    val created_at: Instant?,
    val updated_by: String?,
    val updated_at: Instant?
)

// SYNC__KLF_FEATURE_PLAIN
data class KlfFeaturePlainAvroMessage(
    val id: Int?,
    val name: String,
    val id_feature_category: Int?,
    val created_by: String,
    val created_at: Instant?,
    val updated_by: String?,
    val updated_at: Instant?
)

// SYNC__KLF_FEATURE_TREE
data class KlfFeatureTreeAvroMessage(
    val id: Int?,
    val id_parent: Int?,
    val id_feature_category: Int?,
    val id_feature_plain: Int?,
    val validity_period: String?,
    val created_by: String,
    val created_at: Instant?,
    val updated_by: String?,
    val updated_at: Instant?
)

// SYNC__KLF_FEATURE_CAT_TO_RT
data class KlfFeatureCatToRtAvroMessage(
    val id: Int?,
    val id_right_type: Int,
    val id_feature_category: Int,
    val id_def_feature: Int?,
    val created_by: String,
    val created_at: Instant?,
    val updated_by: String?,
    val updated_at: Instant?
)

// SYNC__KLF_OIP_HIERARCHY
data class KlfOipHierarchyAvroMessage(
    val id: Int?,
    val id_parent: Int,
    val id_oip: Int,
    val created_by: String,
    val created_at: Instant?,
    val updated_by: String?,
    val updated_at: Instant?
)

// SYNC__LOV_OBJECT
data class LovObjectAvroMessage(
    val id: Int?,
    val name: String,
    val table_name: String,
    val where_filter: String?,
    val svc_id: Int
)

// SYNC__LOV_PGE_PG_LAYER
data class LovPgePgLayerAvroMessage(
    val id: Int?,
    val id_pg: Int,
    val sel_value: String
)

// SYNC__LOV_PGE_PG_TO_OBJ
data class LovPgePgToObjAvroMessage(
    val id: Int?,
    val id_obj: Int,
    val code_pg: String
)

// SYNC__LOV_PGE_PGL_DTL
data class LovPgePglDtlAvroMessage(
    val id: Int?,
    val id_pgl: Int,
    val id_property: Int,
    val property_format: String?,
    val default_value: String?,
    val pg_order: Int
)

// SYNC__LOV_PGE_PROP_TYPE
data class LovPgePropTypeAvroMessage(
    val id: Int?,
    val name: String,
    val id_obj: Int?,
    val use_multi_select: Boolean
)

// SYNC__LOV_PGE_PROPERTY
data class LovPgePropertyAvroMessage(
    val id: Int?,
    val code: String,
    val name: String,
    val id_prop_type: Int
)

// SYNC__LOV_PGE_PROPERTY_GROUP
data class LovPgePropertyGroupAvroMessage(
    val id: Int?,
    val code: String,
    val name: String,
    val layer_sel_query: String,
    val svc_id: Int
)
