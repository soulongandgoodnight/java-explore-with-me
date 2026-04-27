package ru.practicum.controller.priv;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.EventRatingDto;
import ru.practicum.dto.MyRatingDto;
import ru.practicum.dto.RatingActionDto;
import ru.practicum.service.RatingService;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/ratings")
@RequiredArgsConstructor
public class PrivateRatingController {

    private final RatingService ratingService;

    @PostMapping("/{eventId}")
    @ResponseStatus(HttpStatus.CREATED)
    public EventRatingDto rateEvent(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @Valid @RequestBody RatingActionDto dto) {
        return ratingService.rateEvent(userId, eventId, dto.getPositive());
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRating(
            @PathVariable Long userId,
            @PathVariable Long eventId) {
        ratingService.removeRating(userId, eventId);
    }

    @GetMapping
    public List<MyRatingDto> getMyRatings(@PathVariable Long userId) {
        return ratingService.getMyRatings(userId);
    }
}
