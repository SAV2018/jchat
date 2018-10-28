package javaChat.client;


import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.ResourceBundle;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Callback;


public class Controller implements Initializable {
    @FXML
    TextField msgField;
    @FXML
    TextArea chat;
    @FXML
    MenuItem server, client, session, state;
    @FXML
    Label flag, auth_error;
    @FXML
    VBox appPanel, entryPanel;
    @FXML
    TextField loginField;
    @FXML
    PasswordField passField;
    @FXML
    CheckBox saveMode;
    @FXML
    ListView<String> list;
    @FXML
    ScrollPane listPane;
    @FXML
    ToolBar status_bar;

    private final String SERVER_ADDR = "localhost";
    private final int SERVER_PORT = 8188;
    private boolean authorized = false;
    private String sessionID = null;
    private String login = null;
    private String nick = null;
    private String password = null;
    private ObservableList<String> clientList;

    // история из [histListSize] последних отправленных сообщений (вкл. служебные)
    private int histListSize = 25; // кол-во хранимых сообщений
    private String[] hisoryList = new String[histListSize];
    private int histIndex = 0;  // указатель последнего записанного элемента
    private int histCursor = 0; // указатель на текущий элемент
    private boolean firstEntry = true; // флаг первого входа в историю

    private Socket socket;
    private DataOutputStream output;
    private DataInputStream input;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // всплывающая подсказка по history
        Tooltip tooltip = new Tooltip();
        tooltip.setText("Для доступа к последним отправленным сообщениям используйте клавиши DOWN и UP");
        msgField.setTooltip(tooltip);

        setAuthorized(false);
        loginField.setText("login1"); passField.setText("pass1"); // ...для удобства отладки
        refreshStatus();
    }

    private void setAuthorized(boolean authorized) {
        this.authorized = authorized;
        if (authorized) { // аквторизован
            appPanel.setVisible(true);
            appPanel.setManaged(true);
            entryPanel.setVisible(false);
            entryPanel.setManaged(false);
        } else { // не авторизован
            nick = null;
            password = null;
            login = null;
            appPanel.setVisible(false);
            appPanel.setManaged(false);
            entryPanel.setVisible(true);
            entryPanel.setManaged(true);
        }
    }

    // проверка состояния и вывод информации о соединении
    private void refreshStatus() {
        if (socket == null || socket.isClosed()) {
            state.setText("Disconnected");
            server.setText("Server: none");
            client.setText("Client: none");
            session.setDisable(false);
            flag.setTextFill(Color.web("#ff0000"));
            flag.setTooltip(new Tooltip("Disconnected"));
        } else {
            state.setText("Connected");
            server.setText("Server: " + socket.getRemoteSocketAddress());
            client.setText("Client: localhost" + socket.getLocalSocketAddress());
            session.setDisable(true);
            flag.setTextFill(Color.web("#00ff00"));
            flag.setTooltip(new Tooltip("Connected"));
        }
    }

    // вывод пользователю сообщения о закрытии или запуске сессии
    private void updateSessionInfo(int event) {
        if (authorized) {
            String s;
            String[] msg = {"Соединение закрыто в ", "Соединение установлено в "};

            if ((event == 1) && (chat.getText().length() > 0)) {
                s = "\n";
            } else {
                s = "";
            }
            Date time = new Date();
            appendMsg(s + "===> " + msg[event] + String.format("%tT  ", time) + String.format("%tF", time) + "\n");
        }
    }

    // запуск сессии
    public void connect() {
        // если соединение уже установлено - выход
        if ((socket != null) && !socket.isClosed()) {
            System.out.println("Соединение с сервером уже установлено!");
            return;
        }
        try {
            try {
                socket = new Socket(SERVER_ADDR, SERVER_PORT);
            } catch (Exception e) {
                System.out.println("Не удалось подключиться к серверу.");
                appendMsg("\n===> Не удалось подключиться к серверу [" + SERVER_ADDR + ":" + SERVER_PORT + "]\n");
                return; // выходим, если соединение не установлено
            }
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            clientList = FXCollections.observableArrayList();
            list.setItems(clientList);
            list.setCellFactory(new Callback<>() {
                @Override
                public ListCell<String> call(ListView<String> param) {
                    return new ListCell<>() {
                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty) {
                                setGraphic(null);
                                setText(null);
                            } else {
                                setText(item);
                                if (item.equals(nick)) {
                                    setStyle("-fx-font-weight:bold;");
                                } else {
                                    setStyle("-fx-font-weight:normal;");
                                }
//                                if (item.equals("ALL")) {
//                                    //setStyle("-fx-border-width:0,0,2;");
//                                }
                            }
                        }
                    };
                }
            });

            Thread thread = new Thread(()-> {
                updateSessionInfo(1);
                refreshStatus();
                if (authorized) { // восстанавливаем сессию
                    sendSID();
                }
                try {
                    while ((socket != null) && !socket.isClosed()) {
                        String msg = input.readUTF();
                        if (msg.startsWith("/")) { // если команда
                            // авторизация прошла успешно
                            if (msg.startsWith("/AUTH_OK ")) {
                                setAuthorized(true);
                                String[] data = msg.split(" ");
                                if (data.length == 3) {
                                    nick = data[1];
                                    sessionID = data[2];
                                }
                                System.out.println("Вы авторизованы по именем [" + nick + "].");
                                appendMsg("===> Вы авторизованы по именем [" + nick + "].\n");
                            }
                            // ошибка авторизации
                            if (msg.equals("/AUTH_ERROR")) {
                                System.out.println("Ошибка авторизации: неверный логин или пароль.");
                                //showAlert("Ошибка авторизации: неверный логин или пароль.");
                                setAuthorized(false);
                                Platform.runLater(() ->
                                        auth_error.setText("Ошибка авторизации: неверный логин или пароль."));
                            }

                            // получение списка клиентов
                            if (msg.startsWith("/LIST ")) {
                                System.out.println(msg);
                                String[] data = msg.split(" ");
                                data[0] = "ALL";
                                Platform.runLater(() -> {
                                    clientList.clear();
//                                    for (String aData : data) {
//                                        clientList.add(aData);
//                                    }
                                    Collections.addAll(clientList, data);
                                });
                            }
                        } else {
                            appendMsg(msg);
                        }
                    }
                } catch (EOFException e) {
                    // закрыто соединение
                } catch (IOException e) {
                    //e.printStackTrace();
                    appendMsg("===> Соединение с сервером потеряно.\n");
                    System.out.println("Соединение с сервером потеряно");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (socket != null) {
                            socket.close();
                            System.out.println("Соединение разорвано клиентом.");
                        }
                        updateSessionInfo(0);
                        refreshStatus();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        } catch (Exception e) {
           e.printStackTrace();
        }
    }

    // добавление сообщения в окно чата
    private void appendMsg(String msg) {
        if (authorized) { // выводим сообщение только внутри чата
            chat.appendText(msg);
        }
    }

    // отправка сообщения на сервер
    private void write(String msg) {
        if (socket != null && !socket.isClosed()) {
            try {
                output.writeUTF(msg);
            } catch (IOException e) {
                System.out.println("Не удалось отправить сообщение.");
                appendMsg("===> Не удалось отправить сообщение\n");
                //e.printStackTrace();
            }
        }
    }

    // обработка отправки сообщения
    public void sendMsg(){
        String msg;
        // не отправляем пустые сообщения
        if (msgField.getText().isEmpty() || (!((msgField.getText().trim()).length() > 0))) return;

        //System.out.println(list.getSelectionModel().getSelectedItem());
        // проверка адресата
        String toNick = list.getSelectionModel().getSelectedItem();
        if (toNick == null || toNick.equals("ALL")) { // private message
            msg = msgField.getText();
        } else {
            msg = "/NICK " + toNick + " " + msgField.getText();
        }

        try {
            if (msg.equals("/END")) {
                disconnect();
            } else {
                //appendMsg(msg + "\n");
                write(msg + "\n");
            }
            hisoryList[histIndex % histListSize] = msg;
            histIndex++;
            msgField.clear();
            msgField.requestFocus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendAuthMsg() {
        // /AUTH login password
        if (loginField.getText().isEmpty() || passField.getText().isEmpty()) {
            auth_error.setText("Ошибка авторизации: не заполнено поле логина или пароля.");
            return;
        }
        login = loginField.getText();
        password = passField.getText();
        // пытаемся установить соединение
        if (socket == null || socket.isClosed()) connect();
        if (socket == null || socket.isClosed()) {
            auth_error.setText("Ошибка авторизации: соединение с сервером не установлено.");
        } else {
            write("/AUTH " + login + " " + passField.getText());
            auth_error.setText("");
        }
        // если отключен режим сохранения данных
        if (!saveMode.isSelected()) { // сбрасываем логин и пароль
            loginField.clear();
            passField.clear();
        }
    }

    private void sendSID() {
        // /SID login session_id
        write("/SID " + login + " " + sessionID);
    }

    public void handler(KeyEvent keyEvent) { // скроллер списка истории
        KeyCode keyCode = keyEvent.getCode();
        //System.out.println(keyEvent.getCode());
        // если нажаты клавиши UP или DOWN
        if ((keyCode == KeyCode.UP) || (keyCode == KeyCode.DOWN)) {
            if (firstEntry) { // первый вход в историю
                histCursor = histIndex % histListSize;
                firstEntry = false;
            }
            switch (keyCode) {
                case DOWN:
                    histCursor = (Math.min(histListSize, histIndex) + histCursor - 1) % Math.min(histListSize, histIndex);
                    break;
                case UP:
                    histCursor = (histCursor + 1) % Math.min(histListSize, histIndex);
                    break;
            }
            // пишем сообщение в поле ввода
            msgField.setText(hisoryList[histCursor]);
        } else { // устанавливаем флаг
            firstEntry = true;
        }
    }

//    public void showAlert(String msg) {
//        Platform.runLater(() -> {
//            Alert alert = new Alert(Alert.AlertType.INFORMATION);
//            alert.setTitle("");
//            alert.setHeaderText(null);
//            alert.setContentText(msg);
//            alert.showAndWait();
//        });
//    }

    // завершение сессии
    public void disconnect() {
        if (socket != null && !socket.isClosed())  {
            write("/END");
        }
        clientList.remove(nick); // исключаем себя из списка, т.к. после отключения от сервера
        // рассылку со списком мы уже не получим
    }

    // отмена авторизации
    public void logout(ActionEvent actionEvent) {
        if (socket != null && !socket.isClosed())  {
            write("/CLOSE");
        }
        setAuthorized(false);
    }

    // завершение работы приложения
    public void closeApp() {
        disconnect();
        Platform.exit();
    }

    public void toggleList() {
        if (listPane.isVisible()) {
            listPane.setVisible(false);
            listPane.setManaged(false);
        } else {
            listPane.setVisible(true);
            listPane.setManaged(false);
        }
    }

    public void toggleStatusBar() {
        if (status_bar.isVisible()) {
            status_bar.setVisible(false);
            status_bar.setManaged(false);
        } else {
            status_bar.setVisible(false);
            status_bar.setManaged(false);
        }
    }

//    public void clickOnClient(MouseEvent mouseEvent) {
//        if (mouseEvent.getClickCount() == 2) {
//            msgField.setText("/NICK " + list.getSelectionModel().getSelectedItem() + " ");
//            msgField.requestFocus(); // каретка в начало поля
//            msgField.selectEnd(); // выделяем всё до конца
//            msgField.forward(); // каретка в конец выделения
//        }
//    }
}