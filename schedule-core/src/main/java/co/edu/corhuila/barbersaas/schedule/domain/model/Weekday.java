package co.edu.corhuila.barbersaas.schedule.domain.model;

import co.edu.corhuila.barbersaas.schedule.domain.model.DomainException.BusinessRuleViolation;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/** day_of_week of barber_schedule: 0 = Sunday … 6 = Saturday, unlike ISO (1 = Monday … 7 = Sunday). */
public final class Weekday {

    private Weekday() {
    }

    public static int of(LocalDate date) {
        return date.getDayOfWeek().getValue() % 7;
    }

    public static int require(int day) {
        if (day < 0 || day > 6) {
            throw new BusinessRuleViolation("The day of the week goes from 0 (Sunday) to 6 (Saturday)");
        }
        return day;
    }

    /** "Monday" for 1, for the messages of the contract. */
    public static String name(int day) {
        return DayOfWeek.of(day == 0 ? 7 : day).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
