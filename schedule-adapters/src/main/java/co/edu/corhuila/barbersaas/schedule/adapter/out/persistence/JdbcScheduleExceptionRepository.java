package co.edu.corhuila.barbersaas.schedule.adapter.out.persistence;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.Filter;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Page;
import co.edu.corhuila.barbersaas.schedule.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.schedule.application.port.out.ScheduleExceptionRepository;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Reads and writes schedule.schedule_exception; every query filters by the barbershop. */
public class JdbcScheduleExceptionRepository implements ScheduleExceptionRepository {

    private static final String COLUMNS =
            "id, barbershop_id, barber_profile_id, exception_date, is_day_off, start_time, end_time, reason";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcScheduleExceptionRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Page<ScheduleException> page(UUID barbershopId, Filter f, Page.Request page) {
        StringBuilder from = new StringBuilder("FROM schedule.schedule_exception WHERE barbershop_id = ?");
        List<Object> args = new ArrayList<>(List.of(barbershopId));
        if (f.barberId() != null) {
            from.append(" AND barber_profile_id = ?");
            args.add(f.barberId());
        }
        if (f.from() != null) {
            from.append(" AND exception_date >= ?");
            args.add(f.from());
        }
        if (f.to() != null) {
            from.append(" AND exception_date <= ?");
            args.add(f.to());
        }
        return JdbcPages.page(jdbc, COLUMNS, new JdbcPages.Query(from.toString(), args,
                "ORDER BY exception_date DESC, id"), (rs, n) -> map(rs), page);
    }

    @Override
    public Optional<ScheduleException> findById(UUID barbershopId, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM schedule.schedule_exception WHERE barbershop_id = ? AND id = ?",
                (rs, n) -> map(rs), barbershopId, id).stream().findFirst();
    }

    @Override
    public Optional<ScheduleException> findOn(UUID barbershopId, UUID barberProfileId, LocalDate date) {
        return jdbc.query("SELECT " + COLUMNS + " FROM schedule.schedule_exception WHERE barbershop_id = ? "
                        + "AND barber_profile_id = ? AND exception_date = ?",
                (rs, n) -> map(rs), barbershopId, barberProfileId, date).stream().findFirst();
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return JdbcIdempotency.find(jdbc, key, operation);
    }

    @Override
    public void saveNew(ScheduleException e, Idempotency.Key key) {
        try {
            tx.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO schedule.schedule_exception (id, barbershop_id, barber_profile_id, "
                                + "exception_date, is_day_off, start_time, end_time, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        e.id(), e.barbershopId(), e.barberProfileId(), e.exceptionDate(), e.dayOff(),
                        e.hours() == null ? null : e.hours().start(), e.hours() == null ? null : e.hours().end(),
                        e.reason());
                JdbcIdempotency.insert(jdbc, key, e.id());
            });
        } catch (DuplicateKeyException dup) {
            if (String.valueOf(dup.getMessage()).contains("uq_schedule_exception_barber_date")) {
                throw new AlreadyExists();
            }
            throw dup;
        }
    }

    @Override
    public void delete(UUID barbershopId, UUID id) {
        jdbc.update("DELETE FROM schedule.schedule_exception WHERE barbershop_id = ? AND id = ?", barbershopId, id);
    }

    private static ScheduleException map(ResultSet rs) throws SQLException {
        LocalTime start = rs.getObject("start_time", LocalTime.class);
        LocalTime end = rs.getObject("end_time", LocalTime.class);
        return new ScheduleException(rs.getObject("id", UUID.class), rs.getObject("barbershop_id", UUID.class),
                rs.getObject("barber_profile_id", UUID.class), rs.getObject("exception_date", LocalDate.class),
                rs.getBoolean("is_day_off"), start == null ? null : new TimeSlot(start, end), rs.getString("reason"));
    }
}
