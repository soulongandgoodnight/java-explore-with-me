package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.CompilationDto;
import ru.practicum.dto.EventShortDto;
import ru.practicum.dto.NewCompilationDto;
import ru.practicum.dto.UpdateCompilationRequest;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.CompilationMapper;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.Compilation;
import ru.practicum.model.Event;
import ru.practicum.model.RequestStatus;
import ru.practicum.repository.CompilationRepository;
import ru.practicum.repository.EventRatingRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.RequestRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {

    private final CompilationRepository compilationRepository;

    private final EventRepository eventRepository;

    private final RequestRepository requestRepository;

    private final EventRatingRepository ratingRepository;

    @Transactional
    public CompilationDto createCompilation(NewCompilationDto dto) {
        Set<Event> events = new HashSet<>();
        if (dto.getEvents() != null && !dto.getEvents().isEmpty()) {
            events = new HashSet<>(
                    eventRepository.findAllById(dto.getEvents()));
        }

        Compilation compilation = Compilation.builder()
                .title(dto.getTitle())
                .pinned(dto.getPinned() != null ? dto.getPinned() : false)
                .events(events)
                .build();

        Compilation saved = compilationRepository.save(compilation);
        return CompilationMapper.toDto(saved, toShortDtos(saved.getEvents()));
    }

    @Transactional
    public void deleteCompilation(Long compId) {
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException(
                    "Compilation with id=" + compId + " was not found");
        }
        compilationRepository.deleteById(compId);
    }

    @Transactional
    public CompilationDto updateCompilation(Long compId,
                                            UpdateCompilationRequest request) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException(
                        "Compilation with id=" + compId + " was not found"));

        if (request.getTitle() != null) {
            compilation.setTitle(request.getTitle());
        }
        if (request.getPinned() != null) {
            compilation.setPinned(request.getPinned());
        }
        if (request.getEvents() != null) {
            Set<Event> events = new HashSet<>(
                    eventRepository.findAllById(request.getEvents()));
            compilation.setEvents(events);
        }

        Compilation saved = compilationRepository.save(compilation);
        return CompilationMapper.toDto(saved, toShortDtos(saved.getEvents()));
    }

    public List<CompilationDto> getCompilations(Boolean pinned,
                                                int from, int size) {
        PageRequest page = PageRequest.of(from / size, size);
        List<Compilation> compilations;
        if (pinned != null) {
            compilations = compilationRepository.findAllByPinned(pinned, page);
        } else {
            compilations = compilationRepository.findAll(page).getContent();
        }
        return compilations.stream()
                .map(c -> CompilationMapper.toDto(c,
                        toShortDtos(c.getEvents())))
                .collect(Collectors.toList());
    }

    public CompilationDto getCompilationById(Long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException(
                        "Compilation with id=" + compId + " was not found"));
        return CompilationMapper.toDto(compilation,
                toShortDtos(compilation.getEvents()));
    }

    private Set<EventShortDto> toShortDtos(Set<Event> events) {
        if (events.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());
        Map<Long, Long> ratingsMap = ratingRepository.getRatingsMapByEventIds(ids);

        return events.stream()
                .map(e -> EventMapper.toEventShortDto(e,
                        requestRepository.countByEventIdAndStatus(
                                e.getId(), RequestStatus.CONFIRMED),
                        0L,
                        ratingsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toSet());
    }
}