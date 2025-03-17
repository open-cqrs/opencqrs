/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.projection.reader;

import com.opencqrs.framework.eventhandler.EventHandling;
import de.dxfrontiers.cqrs.example.domain.reader.api.ReaderRegisteredEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReaderProjector {

    @EventHandling("reader")
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(ReaderRegisteredEvent event, @Autowired ReaderRepository repository) {
        ReaderEntity entity = new ReaderEntity();
        entity.id = event.id();
        repository.save(entity);
    }
}
