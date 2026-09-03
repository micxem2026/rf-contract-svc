package me.rightsflow.contracts.service

import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundException
import me.rightsflow.contracts.dto.request.UserOrgAccessRequest
import me.rightsflow.contracts.dto.request.UserOrgBulkRequest
import me.rightsflow.contracts.dto.response.UserOrgAccessDto
import me.rightsflow.contracts.entity.UserOrgAccess
import me.rightsflow.contracts.repository.OrganizationRepository
import me.rightsflow.contracts.repository.UserOrgAccessRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserOrgAccessService(
    private val repo: UserOrgAccessRepository,
    private val orgRepo: OrganizationRepository,
    private val subProvider: SecuritySubjectProvider
) {

    private val log = LoggerFactory.getLogger(UserOrgAccessService::class.java)

    // ================================================================
    // Чтение
    // ================================================================

    /**
     * Получить все организации, доступные пользователю.
     */
    @Transactional(readOnly = true)
    fun getByUsername(username: String): List<UserOrgAccessDto> =
        repo.findByUsername(username).map { it.toDto() }

    /**
     * Получить всех пользователей, привязанных к организации (с пагинацией).
     */
    @Transactional(readOnly = true)
    fun getByOrg(idOrg: String, pageable: Pageable): Page<UserOrgAccessDto> {
        val irgId = repo.getIdOrg(idOrg)
        requireOrgExists(irgId)
        return repo.findByIdOrg(irgId, pageable).map { it.toDto() }
    }

    // ================================================================
    // Запись
    // ================================================================

    /**
     * Привязать пользователя к одной организации.
     *
     * @throws IllegalArgumentException если привязка уже существует
     * @throws EntityNotFoundException  если организация не найдена
     */
    @Transactional
    fun assign(req: UserOrgAccessRequest): UserOrgAccessDto {

        val orgId = repo.getIdOrg(req.idOrg)
         requireOrgExists(orgId)

        if (repo.existsByUsernameAndIdOrg(req.username, orgId)) {
            throw IllegalArgumentException(
                "Пользователь '${req.username}' уже привязан к организации [ID=${orgId}]"
            )
        }

        val entity = UserOrgAccess(
            username  = req.username,
            idOrg     = orgId,
            createdBy = subProvider.currentSub()
        )
        val saved = repo.save(entity)
        log.info("Пользователь '{}' привязан к организации [ID={}] администратором '{}'",
            req.username, orgId, entity.createdBy)
        return saved.toDto()
    }

    /**
     * Заменить весь список организаций пользователя (replace-семантика).
     *
     * Привязки, не входящие в новый список — удаляются.
     * Новые привязки — добавляются.
     * Существующие совпадающие — остаются без изменений.
     *
     * @return итоговый список привязок пользователя после операции
     */
    @Transactional
    fun assignBulk(req: UserOrgBulkRequest): List<UserOrgAccessDto> {
        // Валидируем все переданные ID организаций
        val orgIds = req.orgIds.map { repo.getIdOrg(it) }
        orgIds.forEach { requireOrgExists(it) }

        val existing    = repo.findByUsername(req.username)
        val existingIds = existing.map { it.idOrg }.toSet()
        val newIds      = orgIds.toSet()

        // Удаляем привязки, которых нет в новом списке
        val toDelete = existing.filter { it.idOrg !in newIds }
        if (toDelete.isNotEmpty()) {
            repo.deleteAll(toDelete)
            log.info("Пользователь '{}': удалено {} привязок к организациям: {}",
                req.username, toDelete.size, toDelete.map { it.idOrg })
        }

        // Добавляем новые привязки
        val toAdd = newIds - existingIds
        val added = toAdd.map { idOrg ->
            repo.save(
                UserOrgAccess(
                    username  = req.username,
                    idOrg     = idOrg,
                    createdBy = subProvider.currentSub()
                )
            )
        }
        if (added.isNotEmpty()) {
            log.info("Пользователь '{}': добавлено {} привязок к организациям: {}",
                req.username, added.size, added.map { it.idOrg })
        }

        return repo.findByUsername(req.username).map { it.toDto() }
    }

    // ================================================================
    // Удаление
    // ================================================================

    /**
     * Отвязать пользователя от конкретной организации.
     *
     * @throws EntityNotFoundException если привязка не найдена
     */
    @Transactional
    fun revoke(username: String, idOrg: String) {
        val orgId = repo.getIdOrg(idOrg)
        if (!repo.existsByUsernameAndIdOrg(username, orgId)) {
            throw EntityNotFoundException(
                "Привязка пользователь='$username', org=[ID=$orgId] не найдена"
            )
        }
        repo.deleteByUsernameAndIdOrg(username, orgId)
        log.info("Пользователь '{}' отвязан от организации [ID={}] администратором '{}'",
            username, orgId, subProvider.currentSub())
    }

    /**
     * Удалить все привязки пользователя.
     */
    @Transactional
    fun revokeAll(username: String) {
        val count = repo.findByUsername(username).size
        repo.deleteAllByUsername(username)
        log.info("Удалены все ({}) привязки пользователя '{}' администратором '{}'",
            count, username, subProvider.currentSub())
    }

    // ================================================================
    // Вспомогательные методы
    // ================================================================

    private fun requireOrgExists(idOrg: Int) {
        if (!orgRepo.existsById(idOrg)) {
            throw EntityNotFoundException("Организация [ID=$idOrg] не найдена в справочнике")
        }
    }

    /**
     * Конвертация entity → DTO с подтягиванием названия организации.
     */
    private fun UserOrgAccess.toDto(): UserOrgAccessDto {
        val org = orgRepo.findById(this.idOrg).orElseThrow { EntityNotFoundException("Организация [ID=$idOrg] не найдена в справочнике") }
        val orgName = org?.name ?: ""
        val code1c = org?.code_1c ?: ""
        return UserOrgAccessDto(
            id        = this.id!!,
            username  = this.username,
            idOrg     = this.idOrg,
            code1c    = code1c,
            orgName   = orgName,
            createdBy = this.createdBy,
            createdAt = this.createdAt
        )
    }
}
