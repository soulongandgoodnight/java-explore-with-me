package ru.practicum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRatingDto {

    private Long eventId;

    private String eventTitle;

    private Long likes;

    private Long dislikes;

    private Long rating;
}