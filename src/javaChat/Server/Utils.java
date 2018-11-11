package javaChat.Server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;


public class Utils {
    private static Logger log = Logger.getLogger(Utils.class.getName());

    public static String getTimeMark() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    public static String getTimeMarkShort() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm"));
    }

    public static void logEvent(String msg) {
        log.info(msg);
        System.out.println(msg);
    }
}
