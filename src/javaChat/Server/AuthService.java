package javaChat.Server;

import java.sql.*;

class AuthService {
    private final String dbname = "jchat.db";
    private Connection db;
    private Statement stmt;
    private PreparedStatement qLogin, qSaveSID, qSID, qUpdateNick;

    void connect() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        db = DriverManager.getConnection("jdbc:sqlite:" + dbname);
        stmt = db.createStatement();
        qLogin = db.prepareStatement("SELECT * FROM users WHERE login=? AND pass=?;");
        qSaveSID = db.prepareStatement("UPDATE users SET session=? WHERE login=?;");
        qSID = db.prepareStatement("SELECT nick FROM users WHERE login=? AND session=?;");
        qUpdateNick = db.prepareStatement("UPDATE users SET nick=? WHERE login=?;");
    }

    // получение ника по логину и паролю
    String getNickByLoginAndPass(String login, String pass) {
        try {
            //ResultSet result = stmt.executeQuery("SELECT * FROM users WHERE login='" + login + "' AND pass='" + pass + "';");
            qLogin.setString(1, login);
            qLogin.setString(2, pass);
            ResultSet result = qLogin.executeQuery();
            while (result.next()) {
                return result.getString("nick");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    void disconnect() {
        try {
            db.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // сохранение метки сессии
    void saveSessionID(String login, String sid) {
        try {
            qSaveSID.setString(2, login);
            qSaveSID.setString(1, sid);
            qSaveSID.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // получение ника по метки сессии (SID)
    String getNickBySessionID(String[] data) {
        try {
            qSID.setString(1, data[1]);
            qSID.setString(2, data[2]);
            ResultSet result = qSID.executeQuery();
            // ...
            if (result.next()) {
                return result.getString("nick");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    int updateNick(String login, String nick) {
        int result;

        try {
            qUpdateNick.setString(1, nick);
            qUpdateNick.setString(2, login);
            qUpdateNick.execute();
            result = 0;
        } catch (SQLException e) {
            result = e.getErrorCode();
            e.printStackTrace();
        }
        System.out.println(result);
        return result;
    }
}
