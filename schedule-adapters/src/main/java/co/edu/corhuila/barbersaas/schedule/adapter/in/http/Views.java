package co.edu.corhuila.barbersaas.schedule.adapter.in.http;

import co.edu.corhuila.barbersaas.schedule.application.port.in.AvailabilityUseCases.FreeSlots;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Page;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleBlock;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** The response schemas of schedule-service.yaml: times always HH:mm, never the entities themselves. */
final class Views {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private Views() {
    }

    static String time(LocalTime t) {
        return t == null ? null : t.format(HH_MM);
    }

    record Meta(int page, int limit, long total, long totalPages) { }

    record PageView<T>(List<T> data, Meta meta) {
        static <D, T> PageView<T> of(Page<D> page, Function<D, T> view) {
            return new PageView<>(page.items().stream().map(view).toList(),
                    new Meta(page.page(), page.limit(), page.total(), page.totalPages()));
        }
    }

    /** BarberSchedule: only current blocks are returned, so isActive is always true. */
    record BlockView(UUID id, UUID barberProfileId, int dayOfWeek, String startTime, String endTime, boolean isActive) { }

    record WeeklyScheduleView(UUID barberProfileId, List<BlockView> slots) {
        static WeeklyScheduleView of(WeeklySchedule w) {
            return new WeeklyScheduleView(w.barberProfileId(), w.blocks().stream()
                    .map((ScheduleBlock b) -> new BlockView(b.id(), w.barberProfileId(), b.dayOfWeek(),
                            time(b.slot().start()), time(b.slot().end()), true))
                    .toList());
        }
    }

    record ExceptionView(UUID id, UUID barberProfileId, LocalDate exceptionDate, boolean isDayOff, String startTime,
                         String endTime, String reason) {
        static ExceptionView of(ScheduleException e) {
            return new ExceptionView(e.id(), e.barberProfileId(), e.exceptionDate(), e.dayOff(),
                    e.hours() == null ? null : time(e.hours().start()), e.hours() == null ? null : time(e.hours().end()),
                    e.reason());
        }
    }

    record SlotView(String startTime, String endTime) {
        static SlotView of(TimeSlot s) {
            return new SlotView(time(s.start()), time(s.end()));
        }
    }

    record AvailabilityView(UUID barberId, UUID serviceId, LocalDate date, List<SlotView> slots) {
        static AvailabilityView of(FreeSlots f) {
            return new AvailabilityView(f.barberId(), f.serviceId(), f.date(), f.slots().stream().map(SlotView::of).toList());
        }
    }
}
