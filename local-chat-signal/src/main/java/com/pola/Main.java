package com.pola;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        // ====== Maria ======
        SignalStoreImpl mariaStore = new SignalStoreImpl("Maria.db");
        ChatClient maria = new ChatClient(mariaStore);

        // maria.registerUser("Maria");
        maria.login("Maria");
        List<Long> messages = new ArrayList<>();
        messages.add(31L);
        messages.add(32L);
        maria.readMessage("Maria", messages);

        // maria.sendMessage("Pablo", "No pablo sos un fraca");
        // maria.receiveMessages();
        // maria.deleteAllMessagesBetween("Pablo", "Maria");

        // ====== Pablo ======
        // SignalStoreImpl pabloStore = new SignalStoreImpl("Pablo.db");
        // ChatClient pablo = new ChatClient(pabloStore);
        // pablo.registerUser("Pablo");
        // pablo.login("Pablo");
        // List<Long> messages = new ArrayList<>();
        // pablo.readMessage("Pablo", messages);

        // pablo.receiveMessages("Pablo");
        // pablo.deleteAllMessagesBetween("Maria", "Pablo");
        // pablo.sendMessage("Maria", "Hola Maria, que onda?");
        // pablo.deleteAllMessagesBetween("Maria", "Pablo");

        // ====== Cerrar sesión ======
        // maria.logout();
        // pablo.logout();
    }

}