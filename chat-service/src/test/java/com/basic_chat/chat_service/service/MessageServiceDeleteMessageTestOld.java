package com.basic_chat.chat_service.service;

/**
 * EJEMPLO: Cómo sería testear el código ORIGINAL sin refactor
 * Este archivo DEMUESTRA LOS PROBLEMAS, no es para ejecutar
 */

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.proto.MessagesProto;

import jakarta.persistence.EntityNotFoundException;

/**
 * PROBLEMAS AL TESTEAR EL CÓDIGO ORIGINAL:
 * 
 * 1. ❌ ACOPLAMIENTO FUERTE AL REPOSITORY
 *    - No puedo testear las validaciones sin mockear el repository
 *    - Las validaciones y la lógica de BD están mezcladas
 * 
 * 2. ❌ LÓGICA CONDICIONAL DIFÍCIL DE TESTEAR
 *    - La línea "if(id == null) return;" es rara en Java
 *    - Un Long NUNCA puede ser null después de Long.valueOf()
 *    - Esa validación es imposible de ejecutar en tests reales
 * 
 * 3. ❌ VALIDACIONES DUPLICADAS
 *    - Valida el id dos veces: implícitamente en Long.valueOf() y luego if(id == null)
 *    - No hay forma de reutilizar esas validaciones
 * 
 * 4. ❌ DIFÍCIL DE TESTEAR CADA CASO
 *    - Para testear la excepción EntityNotFoundException, necesito:
 *      * Mockear el repository.findById() para retornar empty
 *      * Pero también necesito inicializar todo el contexto del método
 *    - La lógica está mezclada, no hay separación de responsabilidades
 */
class MessageServiceDeleteMessageTestOld {

    @Mock
    private MessageRepository messageRepository;

    // ❌ PROBLEMA: Sin MessageValidator, tengo que testear TODO en el servicio
    // private MessageValidator messageValidator;

    @InjectMocks
    private MessageService messageService;

    private Message testMessage;
    private MessagesProto.DeleteMessageRequest deleteRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        testMessage = new Message();
        testMessage.setId(1L);
        
        deleteRequest = MessagesProto.DeleteMessageRequest.newBuilder()
                .setMessageId("1")
                .build();
    }

    /*
     * ❌ PROBLEMA 1: Testear cuando ID es nulo
     * 
     * Problema: Long.valueOf("1") NUNCA retorna null en Java
     * La línea "if(id == null) return;" es código MUERTO
     * No puedo escribir un test que ejecute esa rama
     */
    @Test
    void testDeleteMessageWithNullIdProblematic() {
        // ❌ ¿Cómo hago que Long.valueOf() retorne null?
        // No hay forma, Long.valueOf() siempre retorna Long
        
        // Podría intentar:
        // Long id = null;  // Necesitaría refactor del código original
        // Pero eso requiere cambiar el método, lo cual demuestra que es mal diseñado
    }

    /*
     * ❌ PROBLEMA 2: Testear EntityNotFoundException
     * 
     * Necesito orquestar un Mock complicado
     * El test acaba teniendo más líneas que el código que testea
     */
    @Test
    void testDeleteMessageNotFoundProblematic() {
        // ❌ Necesito SABER que el repository retorna empty
        // Pero eso está acoplado a la implementación del método
        when(messageRepository.findById(1L)).thenReturn(Optional.empty());
        
        // Ahora puedo testear la excepción
        assertThrows(EntityNotFoundException.class, () -> {
            messageService.deleteMessage(deleteRequest);
        });
        
        // ❌ PROBLEMA: No estoy testeando la VALIDACIÓN en sí
        // Estoy testeando "qué pasa si el repository dice que no existe"
        // Pero no puedo testear la validación de forma AISLADA
    }

    /*
     * ❌ PROBLEMA 3: Testear la validación "message.getId() == null"
     * 
     * Ahora necesito un scenario aún MÁS complicado:
     * 1. El mensaje EXISTE en la BD
     * 2. Pero su ID es null (inconsistencia de datos)
     * 3. Necesito mockear el repository para retornar un Message con ID nulo
     */
    @Test
    void testDeleteMessageWithNullMessageIdProblematic() {
        Message invalidMessage = new Message();
        invalidMessage.setId(null);  // Simulamos inconsistencia en BD
        
        // ❌ Necesito preparar el mock de forma muy específica
        when(messageRepository.findById(1L)).thenReturn(Optional.of(invalidMessage));
        
        assertThrows(IllegalArgumentException.class, () -> {
            messageService.deleteMessage(deleteRequest);
        });
        
        // ❌ El test pasará, pero por las razones EQUIVOCADAS
        // No estoy probando la lógica de validación
        // Estoy probando "qué pasa cuando ocurre esta situación extrema"
    }

    /*
     * ❌ PROBLEMA 4: Tests Frágiles (Brittle Tests)
     * 
     * Si mañana cambio la implementación interna del método,
     * todos mis tests podrían romperse innecesariamente
     * Porque están acoplados a los DETALLES DE IMPLEMENTACIÓN
     */
    @Test
    void testDeleteMessageSuccessfullyProblematic() {
        // ❌ Necesito mockear el repository para el happy path
        when(messageRepository.findById(1L)).thenReturn(Optional.of(testMessage));
        
        messageService.deleteMessage(deleteRequest);
        
        verify(messageRepository).deleteById(1L);
        
        // ❌ PROBLEMA: Si cambio la implementación a usar un método diferente,
        // este test se rompe aunque la funcionalidad siga siendo correcta
    }

    /*
     * RESUMEN DE PROBLEMAS DEL CÓDIGO ORIGINAL:
     * 
     * 1. Validaciones y BD están mezcladas → Difícil separar tests
     * 2. Código muerto (if id == null) → Tests incompletos
     * 3. Lógica de validación NO REUTILIZABLE → Duplicación
     * 4. Tests acoplados a implementación → Frágiles
     * 5. Difícil testear SOLO la lógica de negocio → Tests complicados
     * 
     * TODO ESTO DESAPARECE cuando separamos en MessageValidator
     * Porque:
     * - Validaciones están aisladas
     * - Cada componente tiene UNA responsabilidad
     * - Los tests pueden ser independientes
     * - El código es REUTILIZABLE
     */
}
