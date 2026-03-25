package com.pola.view;

import com.dustinredmond.fxtrayicon.FXTrayIcon;
import com.pola.config.HttpConfig;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gestor de la bandeja del sistema (System Tray).
 * Maneja el icono en la bandeja y el timer para cerrar WebSocket cuando la ventana está oculta.
 * La conexión SSE se mantiene activa desde el inicio de la sesión.
 */
public class SystemTrayManager {

    private final Stage primaryStage;
    private FXTrayIcon trayIcon;
    private ScheduledExecutorService wsCloseScheduler;

    private final AtomicBoolean isWindowHidden = new AtomicBoolean(false);
    private final AtomicBoolean trayInitialized = new AtomicBoolean(false);

    private Runnable onWindowShowCallback;
    private Runnable onLogoutCallback;

    public SystemTrayManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /**
     * Inicializa el icono de la bandeja del sistema.
     * Solo se inicializa cuando el usuario oculta la ventana por primera vez.
     * Configura el menú contextual y los manejadores de eventos.
     */
    private void initializeTrayIcon() {
        if (trayInitialized.getAndSet(true)) {
            return;
        }

        Platform.runLater(() -> {
            try {
                Image iconImage = createDefaultIcon();
                trayIcon = new FXTrayIcon(primaryStage, iconImage);
                trayIcon.setTrayIconTooltip("MSG Desktop");

                MenuItem showItem = new MenuItem("Mostrar");
                showItem.setOnAction(event -> onWindowShown());

                MenuItem exitItem = new MenuItem("Salir");
                exitItem.setOnAction(event -> exit());

                trayIcon.addMenuItem(showItem);
                trayIcon.addMenuItem(exitItem);

                trayIcon.show();
                System.out.println("[SystemTrayManager] Icono de bandeja del sistema inicializado");
            } catch (Exception e) {
                System.err.println("[SystemTrayManager] Error al inicializar bandeja del sistema: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Crea un icono por defecto de forma programática.
     * Genera una imagen simple de 16x16 píxeles con un color azul.
     * @return Imagen para el icono de la bandeja
     */
    private Image createDefaultIcon() {
        try {
            Image iconImage = new Image(getClass().getResourceAsStream("/images/icon.png"));
            if (iconImage != null && iconImage.getWidth() > 0) {
                return iconImage;
            }
        } catch (Exception e) {
            System.out.println("[SystemTrayManager] No se encontró icono personalizado, usando icono por defecto");
        }

        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(16, 16);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(javafx.scene.paint.Color.DODGERBLUE);
        gc.fillOval(2, 2, 12, 12);
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.fillText("M", 4, 12);

        javafx.scene.image.WritableImage writableImage = new javafx.scene.image.WritableImage(16, 16);
        canvas.snapshot(null, writableImage);
        return writableImage;
    }

    /**
     * Called when the window is hidden (user clicks X).
     * Schedules WebSocket close after delay. SSE remains active.
     */
    public void onWindowHidden(String userId, String token) {
        isWindowHidden.set(true);

        initializeTrayIcon();

        System.out.println("[SystemTrayManager] Ventana ocultada - programando cierre de WebSocket");
        scheduleWsClose();
    }

    /**
     * Called when the window is shown (user clicks tray icon).
     * Cancels WS close timer and reconnects WS. SSE remains connected.
     */
    public void onWindowShown() {
        if (!isWindowHidden.get()) {
            return;
        }

        System.out.println("[SystemTrayManager] Ventana mostrada desde bandeja");
        isWindowHidden.set(false);

        cancelWsClose();

        Platform.runLater(() -> {
            primaryStage.show();
            primaryStage.toFront();
        });

        if (onWindowShowCallback != null) {
            onWindowShowCallback.run();
        }
    }

    /**
     * Programa el cierre de la conexión WebSocket después del tiempo configurado.
     * Por defecto 60 segundos (HttpConfig.TRAY_WS_CLOSE_DELAY_MS).
     */
    private void scheduleWsClose() {
        cancelWsClose();

        wsCloseScheduler = Executors.newSingleThreadScheduledExecutor();
        long delaySeconds = HttpConfig.TRAY_WS_CLOSE_DELAY_MS / 1000;

        System.out.println("[SystemTrayManager] Programando cierre de WebSocket en " + delaySeconds + " segundos");

        wsCloseScheduler.schedule(() -> {
            System.out.println("[SystemTrayManager] Cerrando conexión WebSocket por inactividad");
            Platform.runLater(() -> {
                if (onLogoutCallback != null) {
                    onLogoutCallback.run();
                }
            });
        }, HttpConfig.TRAY_WS_CLOSE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancela el programado de cierre de WebSocket.
     */
    private void cancelWsClose() {
        if (wsCloseScheduler != null) {
            wsCloseScheduler.shutdownNow();
            wsCloseScheduler = null;
        }
    }

    /**
     * Muestra una notificación nativa del sistema operativo.
     * @param title Título de la notificación
     * @param message Mensaje de la notificación
     */
    public void showNotification(String title, String message) {
        System.out.println("[SystemTrayManager] showNotification() llamado - title: " + title + ", message: " + message);

        Platform.runLater(() -> {
            System.out.println("[SystemTrayManager] Dentro de Platform.runLater - trayIcon: " + (trayIcon != null ? "OK" : "NULL"));

            if (trayIcon != null) {
                System.out.println("[SystemTrayManager] Ejecutando trayIcon.showMessage()");
                trayIcon.showMessage(title, message);
                System.out.println("[SystemTrayManager] showMessage() ejecutado");
            } else {
                System.err.println("[SystemTrayManager] ERROR - trayIcon es null!");
            }
        });
    }

    /**
     * Establece el callback para cuando la ventana se muestra desde el tray.
     * @param callback Runnable a ejecutar
     */
    public void setOnWindowShowCallback(Runnable callback) {
        this.onWindowShowCallback = callback;
    }

    /**
     * Establece el callback para logout o cierre de conexión WebSocket.
     * @param callback Runnable a ejecutar
     */
    public void setOnLogoutCallback(Runnable callback) {
        this.onLogoutCallback = callback;
    }

    /**
     * Cierra completamente la aplicación.
     * Desconecta todos los servicios y sale de la plataforma JavaFX.
     */
    public void exit() {
        System.out.println("[SystemTrayManager] Cerrando aplicación desde bandeja...");
        isWindowHidden.set(false);

        cancelWsClose();

        if (trayIcon != null) {
            trayIcon.hide();
            trayIcon = null;
        }

        Platform.exit();
    }

    /**
     * Apaga el gestor de bandeja del sistema.
     * Libera recursos.
     */
    public void shutdown() {
        System.out.println("[SystemTrayManager] Apagando SystemTrayManager...");
        cancelWsClose();

        if (trayIcon != null) {
            trayIcon.hide();
            trayIcon = null;
        }
    }
}
