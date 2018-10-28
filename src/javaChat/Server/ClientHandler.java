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


public class ClientHandler {
    private final int AUTH_TIMEOUT = 30000; // таймаут авторизации в ms (30 сек.)
    private Server server;
    private String nick;
    private Socket socket;
    private DataOutputStream output;
    private DataInputStream input;
    private String key = "secret_key_for_password_hash"; //
    //private long timein;


    public String getNick() {
        return nick;
    }

    public ClientHandler(Server server, Socket socket) {
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
                            System.out.println("Сервер закрыл соединение [" + socket.getRemoteSocketAddress() + "]");
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
                        System.out.println("От ["  + nick + ":" + socket.getInetAddress() + ":" + socket.getPort() + "]: " + msg);
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
                                        System.out.println("Клиент " + nick + " [" + socket.getInetAddress() + "]:" + socket.getPort() + "] авторизован.");
                                        sendToAllClients("Клиент " + nick + " вошёл в чат.\n", "server");
                                    } else {
                                        System.out.println("Ошибка авторизации.");
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
                                    if (nick != null ) {
                                        System.out.println("Сессия клиента " + nick + " [" + data[2] + "] подтверждена.");
                                        server.updateClient(this);
                                        System.out.println("Клиент " + nick + " [" + socket.getInetAddress() + ":" + socket.getPort() + "] авторизован.");
                                        sendToAllClients("Клиент " + nick + " вошёл в чат.\n", "server");
                                    } else {
                                        System.out.println("Ошибка авторизации.");
                                        sendMsg("/AUTH_ERROR");
                                    }
                                }
                            }
                            // отправить сообщение клиенту/группе клиентов
                            if (msg.startsWith("/NICK ")) { // (!) ники без пробелов
                                String[] data = msg.split(" ", 3);
                                if (data.length == 3) {
                                    if (!sendToClient(data[1], data[2], nick)) {
                                        sendMsg("[server] Не найден клиент с таким ником.\n");
                                    } else {
                                        sendMsg("[" + nick + "] <@ to " + data[1] + "> " + data[2] + "\n");
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
                            // выйти из чата без потери авторизации
                            if (msg.equals("/END")) {
                                System.out.println("Клиент " + nick + " [" + socket.getInetAddress() + ":" + socket.getPort() + "] отключился.");
                                break;
                            }
                            // выйти из чата cо сбросом авторизации
                            if (msg.equals("/CLOSE")) {
                                System.out.println("Клиент " + nick + " [" + socket.getInetAddress() + ":" + socket.getPort() + "] разлогинился.");
                                sendMsg("/AUTH_ERROR");
                                break;
                            }
                        } else {
                            //output.writeUTF("Эхо: " + msg);
                            sendToAllClients(msg, nick);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Клиент " + nick + " [" + socket.getInetAddress() + ":" + socket.getPort() + "] закрыл соединение.");
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
    public void sendMsg(String msg) {
        if (socket != null && !socket.isClosed()) {
            try {
                output.writeUTF(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void killConnect() throws IOException {
        // удаляем клиента из списка
        removeClient(this);
        // сбрасываем метку сессии
        //server.getAuthService().saveSessionID(nick, null);
        sendMsg("/AUTH_ERROR");
        socket.close(); // закрываем соединение
        //
        sendToAllClients("Клиент " + nick + " вышел из чата.\n", "server");
    }

     public static String getMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger number = new BigInteger(1, messageDigest);
            String hashtext = number.toString(16);
            //System.out.println(hashtext);
            // дополняем строку ведущими нулями (до 32 символов)
            //hashtext = "12345678901234567890";
            hashtext = String.format("%32s",  hashtext).replace(" ", "0");
            return hashtext;
        }  catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
