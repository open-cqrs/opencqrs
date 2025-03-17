/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.rest;

import com.opencqrs.framework.command.CommandRouter;
import de.dxfrontiers.cqrs.example.domain.book.api.BorrowBookCommand;
import de.dxfrontiers.cqrs.example.domain.book.api.PurchaseBookCommand;
import de.dxfrontiers.cqrs.example.domain.book.api.ReturnBookCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/book/commands")
public class BookController {

    @Autowired
    private CommandRouter commandRouter;

    @PostMapping("/purchase")
    public ResponseEntity<Void> purchase(@RequestBody @Validated PurchaseBookCommand body, HttpServletRequest request) {
        String id = commandRouter.send(body, Map.of("request-uri", request.getRequestURI()));
        return ResponseEntity.created(URI.create("/api/book/" + id)).build();
    }

    @PostMapping("/borrow")
    public void borrow(@RequestBody @Validated BorrowBookCommand body, HttpServletRequest request) {
        commandRouter.send(body, Map.of("request-uri", request.getRequestURI()));
    }

    @PostMapping("/return")
    public void returnBook(@RequestBody @Validated ReturnBookCommand body, HttpServletRequest request) {
        commandRouter.send(body, Map.of("request-uri", request.getRequestURI()));
    }
}
