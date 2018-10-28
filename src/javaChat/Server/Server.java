package javaChat.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;

public class Server {
    private ServerSocket server;
    private static final int port = 8188;
    // список клиентов, подключившихся к серверу
    private static ArrayList<ClientHandler> clients = new ArrayList<ClientHandler>();
    private AuthService authService;
    private Timer timer = new Timer();

    public AuthService getAuthService() {
        return authService;
    }

    public Timer getTimer() {
        return timer;
    }

    public Server() {
        server = null;
        try {
            authService = new AuthService();
            authService.connect();
            Socket socket = null;
            server = new ServerSocket(port);
            System.out.println("Сервер запущен (порт " + server.getLocalPort() + "), ожидаем подключения...");

            while (true) {
                socket = server.accept();
                System.out.println("Клиент (" + socket.getInetAddress() + ":" + socket.getPort() + ") подключился.");
                new ClientHandler(this, socket);
            }
        } catch (IOException e) {
            System.out.println("Ошибка инициализации сервера (порт " + port + ")");
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("Не удалось запустить службу авторизации.");
            // e.printStackTrace();
        } finally {
            try {
                if (server != null) {   // если объект создан
                    server.close();
                    System.out.println("Сервер остановлен.");
                }
                authService.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void sendToAllClients(String msg, String from) { // (сообщение, от кого)
        // рассылаем сообщение всем клиентам
//        for (ClientHandler c: clients) {
//            c.sendMsg("[" + from + "] " + msg);
//        }
        sendToAllClients("[" + from + "] " + msg);
    }

    public static void sendToAllClients(String msg) { // (сообщение, от кого)
        // рассылаем сообщение всем клиентам
        for (ClientHandler c: clients) {
            c.sendMsg(msg);
        }
    }

    public static void addClient(ClientHandler client) throws IOException {
        ClientHandler c;
        // проверка на уже открытые сессии с таким же ником
        Iterator <ClientHandler> iterator = clients.iterator();
        while (iterator.hasNext()) {
            c = iterator.next();
            if (client.getNick().equals(c.getNick())) {
                System.out.println("Уже есть клиент с таким ником!");
                // если уже есть клиент с таким ником - удаляем его
                c.killConnect();
                break;
            }
        }
        // добавляем клиента в список (в чат)
        clients.add(client);
        sendClientList();
    }

    // удаляем клиента из списка при закрытии соединения
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        sendClientList();
    }

    // удаляем клиента из списка при закрытии соединения
    public static void updateClient(ClientHandler client) {
        if (!clients.contains(client)) {
            // добавляем клиента в список (в чат)
            clients.add(client);
            sendClientList();
        }
    }

    // отправляем сообщение клиенту с ником nick
    public static boolean sendToClient(String nick, String msg, String from) {
        for (ClientHandler c: clients) {
            if (nick.equals(c.getNick())) {
                c.sendMsg("[" + from + "] <@private> " + msg + "\n");
                return true;
            }
        }
        return false;
    }

    //
    public static void sendClientList() {
        StringBuilder list = new StringBuilder("/LIST ");
        for (ClientHandler c: clients) {
            list.append(c.getNick() + " ");
        }
        String msg = list.toString();
        sendToAllClients(msg);
    }
}
