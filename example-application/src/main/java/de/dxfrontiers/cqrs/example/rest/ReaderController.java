/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.rest;

import com.opencqrs.framework.command.CommandRouter;
import de.dxfrontiers.cqrs.example.domain.reader.api.RegisterReaderCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reader/commands")
public class ReaderController {

    @Autowired
    private CommandRouter commandRouter;

    @PostMapping("/register")
    public ResponseEntity<Void> purchase(
            @RequestBody @Validated RegisterReaderCommand body, HttpServletRequest request) {
        UUID id = commandRouter.send(body, Map.of("request-uri", request.getRequestURI()));
        return ResponseEntity.created(URI.create("/api/reader/" + id)).build();
    }
}
