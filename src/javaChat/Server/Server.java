package javaChat.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Timer;

import static javaChat.Server.Utils.getTimeMark;
import static javaChat.Server.Utils.getTimeMarkShort;


class Server {
    private ServerSocket server;
    private static final int port = 8188;
    // список клиентов, подключившихся к серверу
    private static ArrayList<ClientHandler> clients = new ArrayList<>();
    private AuthService authService;
    private Timer timer = new Timer();

    AuthService getAuthService() {
        return authService;
    }

    Timer getTimer() {
        return timer;
    }

    Server() {
        server = null;
        try {
            authService = new AuthService();
            authService.connect();
            Socket socket;
            server = new ServerSocket(port);
            Utils.logEvent("[" + getTimeMark() + "] Сервер запущен (порт " + server.getLocalPort() +
                    "), ожидаем подключения…");
//            System.out.println("[" + getTimeMark() + "] Сервер запущен (порт " + server.getLocalPort() +
//                    "), ожидаем подключения…");

            //noinspection InfiniteLoopStatement
            while (true) {
                socket = server.accept();
                System.out.println("[" + getTimeMark() + "] Клиент (" + socket.getInetAddress() + ":" +
                        socket.getPort() + ") подключился.");
                new ClientHandler(this, socket);
            }
        } catch (IOException e) {
            System.out.println("[" + getTimeMark() + "] Ошибка инициализации сервера (порт " + port + ")");
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println("[" + getTimeMark() + "] Не удалось запустить службу авторизации.");
            // e.printStackTrace();
        } finally {
            try {
                if (server != null) {   // если объект создан
                    server.close();
                    System.out.println("[" + getTimeMark() + "] Сервер остановлен.");
                }
                if (authService != null) {
                    authService.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void sendToAllClients(String msg, String from) { // (сообщение, от кого)
        // рассылаем сообщение всем клиентам
//        for (ClientHandler c: clients) {
//            c.sendMsg("[" + from + "] " + msg);
//        }
        sendToAllClients("[" + from + "●" + getTimeMarkShort() + "] " + msg);
    }

    private static void sendToAllClients(String msg) { // (сообщение)
        // рассылаем сообщение всем клиентам
        for (ClientHandler c: clients) {
            c.sendMsg(msg);
        }
    }

    static void addClient(ClientHandler client) throws IOException {
        ClientHandler c;
        // проверка на уже открытые сессии с таким же ником
        for (ClientHandler client1 : clients) {
            c = client1;
            if (client.getNick().equals(c.getNick())) {
                System.out.println("[" + getTimeMark() + "] Уже есть клиент с таким ником!");
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
    static void removeClient(ClientHandler client) {
        clients.remove(client);
        sendClientList();
    }

    // удаляем клиента из списка при закрытии соединения
    static void updateClient(ClientHandler client) {
        if (!clients.contains(client)) {
            // добавляем клиента в список (в чат)
            clients.add(client);
            sendClientList();
        }
    }

    static void updateNick() {
        sendClientList();
    }

    // отправляем сообщение клиенту с ником nick
    static boolean sendToClient(String nick, String msg, String from) {
        for (ClientHandler c: clients) {
            if (nick.equals(c.getNick())) {
                c.sendMsg("[" + from + "●" + getTimeMarkShort() + "] <private> " + msg + "\n");
                return true;
            }
        }
        return false;
    }

    //
    private static void sendClientList() {
        StringBuilder list = new StringBuilder("/LIST ");
        for (ClientHandler c: clients) {
            list.append(c.getNick()).append(" ");
        }
        String msg = list.toString();
        sendToAllClients(msg);
    }
}
