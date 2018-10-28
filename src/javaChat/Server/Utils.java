package javaChat.Server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Utils {

    public static String getTimeMark() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    public static String getTimeMarkShort() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm"));
    }
}
