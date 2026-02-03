package com.basic_chat.notifiation_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.repository.UserContactRepository;
import com.basic_chat.notifiation_service.repository.UserRepository;
import com.basic_chat.notifiation_service.service.NotificationService;
import com.basic_chat.notifiation_service.service.UserPresenceService;

@SpringBootTest
public class NotificationServiceConcurrencyTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserContactRepository userContactRepository;

    // Mockeamos dependencias que no son el foco del test de DB
    @MockBean
    private StringRedisTemplate redisTemplate;
    @MockBean
    private UserPresenceService userPresenceService;
    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    public void testAddContactConcurrency() throws InterruptedException {
        // Setup de Mocks para evitar NullPointer en la lógica de Redis
        when(redisTemplate.opsForList()).thenReturn(mock(ListOperations.class));

        // 1. Crear usuarios de prueba en la DB
        String userAId = "testUserA_" + System.currentTimeMillis();
        String userBId = "testUserB_" + System.currentTimeMillis();

        User userA = new User();
        userA.setId(userAId);
        userRepository.save(userA);

        User userB = new User();
        userB.setId(userBId);
        userRepository.save(userB);

        // 2. Configurar simulación de concurrencia (5 hilos simultáneos)
        int numberOfThreads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    latch.await(); // Esperar la señal de salida
                    notificationService.addContact(userAId, userBId);
                } catch (Exception e) {
                    // Ignorar excepciones de restricción única si ya estuviera arreglado
                }
            });
        }

        // 3. Disparar todos los hilos
        latch.countDown();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // 4. Verificar duplicados
        long count = userContactRepository.findByUser(userA).stream()
                .filter(c -> c.getContact().getId().equals(userBId))
                .count();

        System.out.println("Registros creados para la misma relación: " + count);

        // Si el test falla (count > 1), confirma que hay un bug de concurrencia
        assertEquals(1, count, "Se encontraron registros duplicados. El método no es thread-safe.");
    }
}


