package com.eventledger.gateway.api;

import com.eventledger.gateway.api.dto.EventResponse;
import com.eventledger.gateway.api.dto.SubmitEventRequest;
import com.eventledger.gateway.service.EventService;
import com.eventledger.gateway.service.EventSubmissionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/events")
@Validated
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> submitEvent(
            @Valid @RequestBody SubmitEventRequest request
    ) {
        EventSubmissionResult result =
                eventService.submitEvent(request);

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity
                .status(status)
                .body(result.event());
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(
            @PathVariable
            @NotBlank(message = "eventId is required")
            String eventId
    ) {
        return eventService.getEvent(eventId);
    }

    @GetMapping
    public List<EventResponse> getEventsForAccount(
            @RequestParam("account")
            @NotBlank(message = "account is required")
            String accountId
    ) {
        return eventService.getEventsForAccount(accountId);
    }
}