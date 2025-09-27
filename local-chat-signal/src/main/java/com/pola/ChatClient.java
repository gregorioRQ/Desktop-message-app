package com.pola;

import java.util.List;
import java.util.logging.Logger;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

public class ChatClient {
    private static final Logger logger = Logger.getLogger(ChatClient.class.getName());

    private String username;

    private SignalStoreImpl signalStore;

    private boolean loggedIn = false;

    public ChatClient(SignalStoreImpl store) {
        this.signalStore = store;
    }

    // 1 - Registrar usuario
    public void registerUser(String username) {
        try {
            logger.info("Registrando usuario: " + username);

            // Generar identidad
            IdentityKeyPair identityKeyPair = KeyHelper.generateIdentityKeyPair();
            int registrationId = KeyHelper.generateRegistrationId(false);

            // Generar preKeys y signedPreKey
            int deviceId = 1;
            int preKeyId = 1;
            int signedPreKeyId = 1;

            PreKeyRecord preKey = KeyHelper.generatePreKeys(preKeyId, 5).get(0);
            SignedPreKeyRecord signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyId);

            // Guardar en DB local (implementado en SignalStoreImpl)
            signalStore.storeIdentityKeyPair(identityKeyPair, registrationId);
            signalStore.storeLocalRegistrationId(registrationId);
            signalStore.storePreKey(preKeyId, preKey);
            signalStore.storeSignedPreKey(signedPreKeyId, signedPreKey);

            // Construir DTO
            RequestRegister dto = new RequestRegister();
            dto.setUsername(username);
            dto.setRegistrationId(registrationId);
            dto.setDeviceId(deviceId);
            dto.setPreKeyId(preKey.getId());
            dto.setPreKeyPublic(preKey.getKeyPair().getPublicKey().serialize());
            dto.setSignedPreKeyId(signedPreKey.getId());
            dto.setSignedPreKeyPublic(signedPreKey.getKeyPair().getPublicKey().serialize());
            dto.setSignedPreKeySignature(signedPreKey.getSignature());
            dto.setIdentityKey(identityKeyPair.getPublicKey().serialize());

            // Mandar al servidor
            ServerApi.registerUser(dto);

            this.username = username;
            logger.info("Usuario " + username + " registrado correctamente.");
        } catch (Exception e) {
            logger.warning("Error registrando usuario: " + e.getMessage());
        }
    }

    // 2 - Login
    public void login(String username) {
        logger.info(username + " intentando login...");

        if (signalStore.loadIdentityKeyPair() != null) {
            signalStore.getLocalRegistrationId();
            this.username = username;
            loggedIn = true;
            logger.info("Usuario " + username + " logueado. Claves cargadas en memoria.");
        } else {
            logger.warning("No existen claves para " + username + ". Debe registrarse primero.");
        }
    }

    public void sendMessage(String to, String message) {
        try {
            SignalProtocolAddress address = new SignalProtocolAddress(to, 1); // deviceId fijo 1

            // 1. Revisar si ya existe sesión
            SessionRecord existing = signalStore.loadSession(address);

            if (existing == null || existing.isFresh()) {
                // No hay sesión previa -> crearla con el PreKeyBundle
                PreKeyBundleDTO dto = ServerApi.getPreKeyBundle(to);
                if (dto == null) {
                    logger.warning("No se encontró PreKeyBundle para " + to);
                    return;
                }

                PreKeyBundle bundle = new PreKeyBundle(
                        dto.registrationId,
                        dto.deviceId,
                        dto.preKeyId,
                        Curve.decodePoint(dto.preKeyPublic, 0),
                        dto.signedPreKeyId,
                        Curve.decodePoint(dto.signedPreKeyPublic, 0),
                        dto.signedPreKeySignature,
                        new IdentityKey(dto.identityKey, 0));

                SessionBuilder builder = new SessionBuilder(signalStore, address);
                builder.process(bundle);

                logger.info("Sesión creada con " + to + " usando su PreKeyBundle");
            } else {
                logger.info("Reutilizando sesión existente con " + to);
            }

            // 2. Cifrar con la sesión actual
            SessionCipher cipher = new SessionCipher(signalStore, address);
            CiphertextMessage encrypted = cipher.encrypt(message.getBytes());

            // 3. Guardar mensaje en el "servidor"

            MessageRequest mr = new MessageRequest();
            mr.setSender(username);
            mr.setReceiver(to);
            mr.setContent(encrypted.serialize());
            mr.setCreated_at(System.currentTimeMillis());

            ServerApi.sendMessage(mr);
            logger.info(username + " envió mensaje cifrado a " + to);

        } catch (Exception e) {
            logger.warning("Error enviando mensaje: " + e.getMessage());
        }
    }

    public void receiveMessages(String username) {
        List<MessageResponse> messages = ServerApi.getMessages(username);
        for (MessageResponse mr : messages) {
            String sender = mr.getSender();
            byte[] msgBytes = mr.getContent();
            SignalProtocolAddress address = new SignalProtocolAddress(sender, 1);

            try {
                // Intentar primero como PreKeySignalMessage
                PreKeySignalMessage preKeyMsg = new PreKeySignalMessage(msgBytes);

                // OJO: acá sí corresponde crear SessionCipher
                SessionCipher cipher = new SessionCipher(signalStore, address);
                byte[] decrypted = cipher.decrypt(preKeyMsg);
                signalStore.storeMessage(sender, mr.getReceiver(), new String(decrypted));
                logger.info("Mensaje inicial de " + sender + ": " + new String(decrypted));
            } catch (Exception e) {
                try {
                    // Mensaje normal: usar la sesión ya guardada
                    if (!signalStore.containsSession(address)) {
                        logger.warning("No existe sesión previa con " + sender);
                        continue;
                    }

                    // Solo crear el cipher si ya existe sesión previa
                    SessionCipher cipher = new SessionCipher(signalStore, address);
                    SignalMessage signalMsg = new SignalMessage(msgBytes);
                    byte[] decrypted = cipher.decrypt(signalMsg);
                    signalStore.storeMessage(sender, mr.getReceiver(), new String(decrypted));
                    logger.info("Mensaje de " + sender + ": " + new String(decrypted));
                } catch (Exception ex) {
                    logger.warning("No se pudo desencriptar mensaje de " + sender + ": " + ex.getMessage());
                }
            }
        }

    }

    public List<String> getLocalMessages(String username) {
        return signalStore.getLocalMessages(username);
    }

    public void deleteAllMessagesBetween(String sender, String receiver) {
        try {
            ServerApi.deleteMessageBetweenUsers(sender, receiver);
            logger.info("Todos los mensajes entre " + sender + " y " + receiver + " han sido eliminados.");
        } catch (Exception e) {
            logger.warning("Error eliminando mensajes entre " + sender + " y " + receiver + ": " + e.getMessage());
        }
    }

    public void readMessage(String receiver, List<Long> messageId) {
        ServerApi.readMessages(receiver, messageId);
    }

    // 4 - Logout
    public void logout() {
        loggedIn = false;
        logger.info("Usuario " + username + " cerró sesión.");
    }
}
