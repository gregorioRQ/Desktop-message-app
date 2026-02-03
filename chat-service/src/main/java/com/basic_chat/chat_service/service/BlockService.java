package com.basic_chat.chat_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.chat_service.models.ContactBlock;
import com.basic_chat.chat_service.repository.ContactBlockRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class BlockService {
    
    private final ContactBlockRepository blockRepository;

    public BlockService(ContactBlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    /**
     * Bloquea a un usuario. Es una operación idempotente.
     * 
     * @param blocker usuario que realiza el bloqueo
     * @param blocked usuario a ser bloqueado
     * @return true si el bloqueo fue exitoso, false si el blocker intentó bloquearse a sí mismo
     */
    @Transactional
    public boolean blockUser(String blocker, String blocked) {
        if (!isValidUsernames(blocker, blocked)) {
            log.warn("Intento de bloqueo con valores inválidos - blocker: {}, blocked: {}", blocker, blocked);
            return false;
        }

        if (isSelfBlockAttempt(blocker, blocked)) {
            log.info("Usuario intenta bloquearse a sí mismo - usuario: {}", blocker);
            return false;
        }

        if (isAlreadyBlocked(blocker, blocked)) {
            log.debug("El bloqueo ya existe - blocker: {}, blocked: {}", blocker, blocked);
            return true; // Idempotencia: consideramos exitosa la operación
        }

        try {
            blockRepository.save(new ContactBlock(blocker, blocked));
            log.info("Usuario bloqueado exitosamente - blocker: {}, blocked: {}", blocker, blocked);
            return true;
        } catch (Exception e) {
            log.error("Error al guardar bloqueo en base de datos - blocker: {}, blocked: {}", blocker, blocked, e);
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Desbloquea a un usuario. Es una operación idempotente.
     * 
     * @param blocker usuario que realiza el desbloqueo
     * @param blocked usuario a ser desbloqueado
     * @return true si el desbloqueo fue exitoso o el bloqueo no existía
     */
    @Transactional
    public boolean unblockUser(String blocker, String blocked) {
        if (!isValidUsernames(blocker, blocked)) {
            log.warn("Intento de desbloqueo con valores inválidos - blocker: {}, blocked: {}", blocker, blocked);
            return false;
        }

        try {
            long deletedCount = blockRepository.deleteByBlockerAndBlocked(blocker, blocked);
            
            if (deletedCount > 0) {
                log.info("Usuario desbloqueado exitosamente - blocker: {}, blocked: {}", blocker, blocked);
            } else {
                log.debug("No existía bloqueo para remover - blocker: {}, blocked: {}", blocker, blocked);
            }
            
            return true; // Idempotencia: exitoso si se eliminó o no existía
        } catch (Exception e) {
            log.error("Error al eliminar bloqueo de base de datos - blocker: {}, blocked: {}", blocker, blocked, e);
            return false;
        }
    }

    /**
     * Verifica si el envío de mensajes está bloqueado.
     * 
     * @param sender usuario que intenta enviar el mensaje
     * @param recipient usuario destinatario del mensaje
     * @return true si el recipient ha bloqueado al sender
     */
    public boolean isBlocked(String sender, String recipient) {
        if (!isValidUsernames(sender, recipient)) {
            log.warn("Verificación de bloqueo con valores inválidos - sender: {}, recipient: {}", sender, recipient);
            return false;
        }

        try {
            boolean blocked = blockRepository.existsByBlockerAndBlocked(recipient, sender);
            
            if (blocked) {
                log.debug("Mensaje bloqueado - sender: {}, recipient: {}", sender, recipient);
            }
            
            return blocked;
        } catch (Exception e) {
            log.error("Error verificando estado de bloqueo - sender: {}, recipient: {}", sender, recipient, e);
            return false; // En caso de error, permitimos el envío (fail open)
        }
    }

    /**
     * Verifica si un usuario está bloqueado por otro.
     * 
     * @param blocker usuario que podría haber bloqueado
     * @param potentiallyBlocked usuario que podría estar bloqueado
     * @return true si blocker ha bloqueado a potentiallyBlocked
     */
    public boolean isUserBlocked(String blocker, String potentiallyBlocked) {
        if (!isValidUsernames(blocker, potentiallyBlocked)) {
            log.warn("Verificación de usuario bloqueado con valores inválidos - blocker: {}, blocked: {}", 
                    blocker, potentiallyBlocked);
            return false;
        }

        try {
            return blockRepository.existsByBlockerAndBlocked(blocker, potentiallyBlocked);
        } catch (Exception e) {
            log.error("Error verificando si usuario está bloqueado - blocker: {}, blocked: {}", 
                    blocker, potentiallyBlocked, e);
            return false;
        }
    }

    /**
     * Obtiene el total de usuarios bloqueados por un usuario.
     * 
     * @param blocker usuario del cual obtener bloques
     * @return cantidad de usuarios bloqueados
     */
    public long getBlockedUsersCount(String blocker) {
        if (!isValidUsername(blocker)) {
            log.warn("Búsqueda de bloques con username inválido - blocker: {}", blocker);
            return 0;
        }

        try {
            return blockRepository.countByBlocker(blocker);
        } catch (Exception e) {
            log.error("Error contando usuarios bloqueados - blocker: {}", blocker, e);
            return 0;
        }
    }

    // ==================== Métodos privados de validación ====================

    /**
     * Valida que ambos usernames sean válidos (no nulos ni vacíos).
     */
    private boolean isValidUsernames(String username1, String username2) {
        return isValidUsername(username1) && isValidUsername(username2);
    }

    /**
     * Valida que un username sea válido (no nulo ni vacío).
     */
    private boolean isValidUsername(String username) {
        return username != null && !username.trim().isEmpty();
    }

    /**
     * Verifica si el usuario intenta bloquearse a sí mismo.
     */
    private boolean isSelfBlockAttempt(String blocker, String blocked) {
        return blocker.equalsIgnoreCase(blocked);
    }

    /**
     * Verifica si un bloqueo ya existe.
     */
    private boolean isAlreadyBlocked(String blocker, String blocked) {
        return blockRepository.existsByBlockerAndBlocked(blocker, blocked);
    }
}
