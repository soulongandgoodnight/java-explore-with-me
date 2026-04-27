package ru.practicum.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.dto.EventRatingDto;
import ru.practicum.model.Event;

@UtilityClass
public class RatingMapper {

    public EventRatingDto toEventRatingDto(Event event,
                                           Long likes,
                                           Long dislikes) {
        return EventRatingDto.builder()
                .eventId(event.getId())
                .eventTitle(event.getTitle())
                .likes(likes)
                .dislikes(dislikes)
                .rating(likes - dislikes)
                .build();
    }
}