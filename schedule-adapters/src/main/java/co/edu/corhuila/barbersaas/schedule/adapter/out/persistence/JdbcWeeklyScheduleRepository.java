package co.edu.corhuila.barbersaas.schedule.adapter.out.persistence;

import co.edu.corhuila.barbersaas.schedule.application.port.out.WeeklyScheduleRepository;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleBlock;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Reads and writes schedule.barber_schedule, owned by barber-saas-schedule-db. It knows SQL; the domain does not. */
public class JdbcWeeklyScheduleRepository implements WeeklyScheduleRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcWeeklyScheduleRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public WeeklySchedule find(UUID barbershopId, UUID barberProfileId) {
        List<ScheduleBlock> blocks = jdbc.query("SELECT id, day_of_week, start_time, end_time "
                        + "FROM schedule.barber_schedule WHERE barbershop_id = ? AND barber_profile_id = ? AND is_active "
                        + "ORDER BY day_of_week, start_time",
                (rs, n) -> new ScheduleBlock(rs.getObject("id", UUID.class), rs.getInt("day_of_week"),
                        new TimeSlot(rs.getObject("start_time", LocalTime.class),
                                rs.getObject("end_time", LocalTime.class))),
                barbershopId, barberProfileId);
        return WeeklySchedule.of(barbershopId, barberProfileId, blocks);
    }

    /**
     * DEC-SCHED-01 in one transaction: the current blocks are deactivated (is_active = false, so they
     * are never returned again) and the new set is inserted. Nothing is left half replaced.
     */
    @Override
    public void replace(WeeklySchedule schedule) {
        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE schedule.barber_schedule SET is_active = false "
                            + "WHERE barbershop_id = ? AND barber_profile_id = ? AND is_active",
                    schedule.barbershopId(), schedule.barberProfileId());
            for (ScheduleBlock b : schedule.blocks()) {
                jdbc.update("INSERT INTO schedule.barber_schedule (id, barbershop_id, barber_profile_id, day_of_week, "
                                + "start_time, end_time, is_active) VALUES (?, ?, ?, ?, ?, ?, true)",
                        b.id(), schedule.barbershopId(), schedule.barberProfileId(), b.dayOfWeek(), b.slot().start(),
                        b.slot().end());
            }
        });
    }
}
