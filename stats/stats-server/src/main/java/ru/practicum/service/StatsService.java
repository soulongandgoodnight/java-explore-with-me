package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStatsDto;
import ru.practicum.mapper.StatsMapper;
import ru.practicum.model.EndpointHit;
import ru.practicum.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final StatsRepository statsRepository;

    @Transactional
    public void saveHit(EndpointHitDto hitDto) {
        EndpointHit hit = StatsMapper.toEndpointHit(hitDto);
        statsRepository.save(hit);
    }

    public List<ViewStatsDto> getStats(LocalDateTime start,
                                       LocalDateTime end,
                                       List<String> uris,
                                       boolean unique) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "Start date must be before end date");
        }

        List<ru.practicum.model.ViewStatsProjection> projections;

        if (uris == null || uris.isEmpty()) {
            projections = unique
                    ? statsRepository.findAllStatsUnique(start, end)
                    : statsRepository.findAllStats(start, end);
        } else {
            projections = unique
                    ? statsRepository.findStatsByUrisUnique(start, end, uris)
                    : statsRepository.findStatsByUris(start, end, uris);
        }

        return projections.stream()
                .map(StatsMapper::toViewStatsDto)
                .collect(Collectors.toList());
    }
}