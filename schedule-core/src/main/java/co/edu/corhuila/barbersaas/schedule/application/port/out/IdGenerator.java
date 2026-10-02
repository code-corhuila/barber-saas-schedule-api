package co.edu.corhuila.barbersaas.schedule.application.port.out;

import java.util.UUID;

public interface IdGenerator {

    UUID next();
}
