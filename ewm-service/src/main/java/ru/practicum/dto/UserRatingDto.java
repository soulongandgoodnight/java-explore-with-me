package ru.practicum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRatingDto {

    private Long userId;

    private String userName;

    private Long totalRating;

    private Long eventsCount;
}