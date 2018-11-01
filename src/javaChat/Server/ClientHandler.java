package javaChat.Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TimerTask;

import static javaChat.Server.Server.*;
import static javaChat.Server.Utils.getTimeMark;
import static javaChat.Server.Utils.getTimeMarkShort;


class ClientHandler {
    private final int AUTH_TIMEOUT = 10000; // таймаут авторизации в ms (10 сек.)
    private Server server;
    private String nick;
    private String login;
    private Socket socket;
    private DataOutputStream output;
    private DataInputStream input;
    private String key = "secret_key_for_password_hash"; //


    String getNick() {
        return nick;
    }

    ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            this.output = new DataOutputStream(socket.getOutputStream());
            this.input = new DataInputStream(socket.getInputStream());

            // устанавливаем задание для таймера:
            // разорвать соединение, если клиент не был авторизован за время AUTH_TIMEOUT
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    if (nick == null) { // если клиент не авторизован
                        try {
                            socket.close();
                            System.out.println("[" + getTimeMark() + "] Сервер закрыл соединение [" +
                                    socket.getRemoteSocketAddress() + "]");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            //
            server.getTimer().schedule(task, AUTH_TIMEOUT);

            new Thread(() -> {
                try {
                    while (true) {
                        String msg = input.readUTF();
                        System.out.println("[" + getTimeMark() + "] Сообщение от [" +
                                nick + ":" + socket.getInetAddress() + ":" + socket.getPort() + "]: " + msg);

                        if (msg.startsWith("/")) { // если строка начинается с "/"
                            //System.out.println(msg);
                            // запрос на авторизацию
                            if (msg.startsWith("/AUTH ")) { // /AUTH login password
                                String[] data = msg.split(" ");
                                if (data.length == 3) {
                                    data[2] = getMD5(key + data[1] + data[2]);
                                    //System.out.println(data[1] +" "+ data[2]); //Platform.exit();
                                    nick = server.getAuthService().getNickByLoginAndPass(data[1], data[2]);

                                    if (nick != null) {
                                        String sid = getMD5((data[1] + System.currentTimeMillis()));
                                        //System.out.println("session: " + sid);
                                        server.getAuthService().saveSessionID(data[1], sid);
                                        sendMsg("/AUTH_OK " + nick + " " + sid);
                                        addClient(this); // (!) с проверкой
                                        login = data[1]; // сохраняем логин клиента
                                        System.out.println("[" + getTimeMark() + "] Клиент " + nick + " [" +
                                                socket.getInetAddress() + ":" + socket.getPort() + "] авторизован.");
                                        sendToAllClients("Клиент " + nick + " вошёл в чат.\n", "server");
                                    } else {
                                        System.out.println("[" + getTimeMark() + "] Ошибка авторизации.");
                                        sendMsg("/AUTH_ERROR");
                                    }
                                }
                            }

                            // проверка сессии (после переподключения)
                            if (msg.startsWith("/SID ")) { // /SID login session_id
                                //System.out.println(msg);
                                String[] data = msg.split(" ");
                                if (data.length == 3) {
                                    nick = server.getAuthService().getNickBySessionID(data);

                                    if (nick != null) {
                                        System.out.println("[" + getTimeMark() + "] Сессия клиента " + nick +
                                                " [" + data[2] + "] подтверждена.");
                                        login = data[1];
                                        updateClient(this);
                                        System.out.println("[" + getTimeMark() + "] Клиент " + nick + " [" +
                                                socket.getInetAddress() + ":" + socket.getPort() + "] авторизован.");
                                        sendToAllClients("Клиент " + nick + " вошёл в чат.\n", "server");
                                    } else {
                                        System.out.println("[" + getTimeMark() + "] Ошибка авторизации.");
                                        sendMsg("/AUTH_ERROR");
                                    }
                                }
                            }

                            // отправить сообщение клиенту/группе клиентов
                            if (msg.startsWith("/NICK ")) { // (!) ник без пробелов
                                String[] data = msg.split(" ", 3);
                                if (data.length == 3) {
                                    if (!sendToClient(data[1], data[2], nick)) {
                                        sendMsg("[server●" + getTimeMarkShort() +
                                                "] Не найден клиент с таким ником (" + data[1] + ").\n");
                                    } else {
                                        sendMsg("[" + nick + "●" + getTimeMarkShort() + "] <private to " +
                                                data[1] + "> " + data[2] + "\n");
                                    }
                                }
                                // вариант через substring()
//                                if (data.length>2) {
//                                    // вырезаем сообщение (могут быть пробелы!)
//                                    msg = msg.substring(7 + data[1].length());
//                                    msg = msg.trim();
//                                    // отправляем личное сообщение
//                                    if (!sendToClient(data[1], msg, nick)) {
//                                        sendMsg("[server] Не найден клиент с таким ником.");
//                                    }
//                                }
                            }

                            // запрос на смену ника
                            if (msg.startsWith("/NEW ")) { // новый ник
                                String[] data = msg.split(" ", 2);
                                //System.out.println(data[0] + " " + data[1]);
                                if (data.length == 2) {
                                    switch (server.getAuthService().updateNick(login, data[1])) {
                                        case 0:
                                            nick = data[1];
                                            sendMsg("/NEW OK " + nick);
                                            updateNick();
                                            break;
                                        case 19: // ник уже используется
                                            sendMsg("/NEW USED " + data[1]);
                                            break;
                                        default:
                                            sendMsg("/NEW ERROR");
                                    }
                                }
                            }

                            // выйти из чата без потери авторизации
                            if (msg.equals("/END")) {
                                System.out.println("[" + getTimeMark() + "] Клиент " + nick +
                                        " [" + socket.getInetAddress() + ":" + socket.getPort() + "] отключился.");
                                break;
                            }
                            // выйти из чата cо сбросом авторизации
                            if (msg.equals("/CLOSE")) {
                                System.out.println("[" + getTimeMark() + "] Клиент " + nick + " [" +
                                        socket.getInetAddress() + ":" + socket.getPort() + "] разлогинился.");
                                sendMsg("/AUTH_ERROR");
                                break;
                            }
                        } else {
                            //output.writeUTF("Эхо: " + msg);
                            sendToAllClients(msg, nick);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("[" + getTimeMark() + "] Клиент " + nick + " [" + socket.getInetAddress() +
                            ":" + socket.getPort() + "] закрыл соединение.");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        socket.close(); //
                        removeClient(this);
                        if (nick != null) { // только авторизованные клиенты
                            sendToAllClients("Клиент " + nick + " вышел из чата.\n", "server");
                        }
                        nick = null;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // отправляем сообщение
    void sendMsg(String msg) {
        if (socket != null && !socket.isClosed()) {
            try {
                output.writeUTF(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void killConnect() throws IOException {
        // удаляем клиента из списка
        removeClient(this);
        // сбрасываем метку сессии
        //server.getAuthService().saveSessionID(nick, null);
        sendMsg("/AUTH_ERROR");
        socket.close(); // закрываем соединение
        //
        sendToAllClients("Клиент " + nick + " вышел из чата.\n", "server");
    }

    private static String getMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger number = new BigInteger(1, messageDigest);
            String hashtext = number.toString(16);
            //System.out.println(hashtext);
            // дополняем строку ведущими нулями (до 32 символов)
            //hashtext = "12345678901234567890";
            hashtext = String.format("%32s", hashtext).replace(" ", "0");
            return hashtext;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
