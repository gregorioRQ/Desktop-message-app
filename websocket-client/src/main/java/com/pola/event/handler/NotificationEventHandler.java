package com.pola.event.handler;

import com.pola.event.SseEvent;
import com.pola.event.SseEventHandler;
import com.pola.event.SseEventType;
import com.pola.view.SystemTrayManager;
import com.pola.view.ViewManager;

/**
 * Handler para eventos de notificación generales.
 * Procesa eventos SSE de tipo NOTIFICATION o MESSAGE.
 * Muestra notificaciones en el system tray cuando la ventana está oculta.
 */
public class NotificationEventHandler implements SseEventHandler {

    private final ViewManager viewManager;
    private final SystemTrayManager systemTrayManager;

    public NotificationEventHandler(ViewManager viewManager, SystemTrayManager systemTrayManager) {
        this.viewManager = viewManager;
        this.systemTrayManager = systemTrayManager;
    }

    @Override
    public boolean canHandle(SseEventType type) {
        return type == SseEventType.NOTIFICATION || type == SseEventType.MESSAGE;
    }

    @Override
    public void handle(SseEvent event) {
        if (event == null) {
            return;
        }

        String message = event.getData();

        // Mostrar notificación en system tray si la ventana está oculta
        if (viewManager != null && !viewManager.isWindowVisible()) {
            if (systemTrayManager != null) {
                systemTrayManager.showNotification("MSG Desktop", message);
                System.out.println("[NotificationEventHandler] Notificación mostrada: " + message);
            } else {
                System.err.println("[NotificationEventHandler] SystemTrayManager es null");
            }
        } else {
            System.out.println("[NotificationEventHandler] Ventana visible, no se muestra notificación: " + message);
        }
    }
}
