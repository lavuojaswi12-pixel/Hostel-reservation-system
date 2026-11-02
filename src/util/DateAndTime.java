package util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final public class DateAndTime {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private DateAndTime() {}

    public static LocalDate getDate() {return LocalDate.now();}
    public static String getTime() {return LocalTime.now().format(TIME_FORMAT);}
}
