package javaChat.Server;

import java.sql.*;

public class AuthService {
    private final String dbname = "jchat.db";
    private Connection db;
    private Statement stmt;
    private PreparedStatement qLogin, qSaveSID, qSID;

    public void connect() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        db = DriverManager.getConnection("jdbc:sqlite:" + dbname);
        stmt = db.createStatement();
        qLogin = db.prepareStatement("SELECT * FROM users WHERE login=? AND pass=?;");
        qSaveSID = db.prepareStatement("UPDATE users SET session=? WHERE login=?;");
        qSID = db.prepareStatement("SELECT nick FROM users WHERE login=? AND session=?;");
    }

    // получение ника по логину и паролю
    public String getNickByLoginAndPass(String login, String pass) {
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

    public void disconnect() {
        try {
            db.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // сохранение метки сессии
    public void saveSessionID(String login, String sid) {
        try {
            qSaveSID.setString(2, login);
            qSaveSID.setString(1, sid);
            qSaveSID.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // получение ника по метки сессии (SID)
    public String getNickBySessionID(String[] data) {
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
}
