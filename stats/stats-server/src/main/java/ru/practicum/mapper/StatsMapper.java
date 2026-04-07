package ru.practicum.mapper;

import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStatsDto;
import ru.practicum.model.EndpointHit;
import ru.practicum.model.ViewStatsProjection;

public class StatsMapper {

    private StatsMapper() {
    }

    public static EndpointHit toEndpointHit(EndpointHitDto dto) {
        return EndpointHit.builder()
                .app(dto.getApp())
                .uri(dto.getUri())
                .ip(dto.getIp())
                .timestamp(dto.getTimestamp())
                .build();
    }

    public static ViewStatsDto toViewStatsDto(ViewStatsProjection projection) {
        return ViewStatsDto.builder()
                .app(projection.getApp())
                .uri(projection.getUri())
                .hits(projection.getHits())
                .build();
    }
}