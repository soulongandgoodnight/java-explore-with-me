package ru.practicum.controller.pub;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.EventRatingDto;
import ru.practicum.dto.UserRatingDto;
import ru.practicum.service.RatingService;

import java.util.List;

@Validated
@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
public class PublicRatingController {

    private final RatingService ratingService;

    @GetMapping("/events/{eventId}")
    public EventRatingDto getEventRating(@PathVariable Long eventId) {
        return ratingService.getEventRating(eventId);
    }

    @GetMapping("/events/top")
    public List<EventRatingDto> getTopEvents(
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(100) int size) {
        return ratingService.getTopEvents(size);
    }

    @GetMapping("/users/{userId}")
    public UserRatingDto getUserRating(@PathVariable Long userId) {
        return ratingService.getUserRating(userId);
    }

    @GetMapping("/users/top")
    public List<UserRatingDto> getTopOrganizers(
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(100) int size) {
        return ratingService.getTopOrganizers(size);
    }
}