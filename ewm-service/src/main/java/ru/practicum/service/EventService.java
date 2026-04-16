package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.StatsClient;
import ru.practicum.dto.EventFullDto;
import ru.practicum.dto.EventRequestStatusUpdateRequest;
import ru.practicum.dto.EventRequestStatusUpdateResult;
import ru.practicum.dto.EventShortDto;
import ru.practicum.dto.NewEventDto;
import ru.practicum.dto.ParticipationRequestDto;
import ru.practicum.dto.UpdateEventAdminRequest;
import ru.practicum.dto.UpdateEventUserRequest;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.EventMapper;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.AdminStateAction;
import ru.practicum.model.Category;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.Location;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.model.RequestStatus;
import ru.practicum.model.User;
import ru.practicum.model.UserStateAction;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.RequestRepository;
import ru.practicum.repository.UserRepository;
import ru.practicum.ViewStatsDto;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;

    private final UserRepository userRepository;

    private final CategoryRepository categoryRepository;

    private final RequestRepository requestRepository;

    private final StatsClient statsClient;

    private static final String APP_NAME = "ewm-main-service";

    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        checkUserExists(userId);
        PageRequest page = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, page);
        return events.stream()
                .map(e -> EventMapper.toEventShortDto(e,
                        getConfirmedRequests(e.getId()), 0L))
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "User with id=" + userId + " was not found"));
        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException(
                        "Category with id=" + dto.getCategory()
                                + " was not found"));

        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new IllegalArgumentException(
                    "Event date must be at least 2 hours from now");
        }

        Event event = EventMapper.toEvent(dto, user, category);
        return EventMapper.toEventFullDto(eventRepository.save(event), 0L, 0L);
    }

    public EventFullDto getUserEventById(Long userId, Long eventId) {
        checkUserExists(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));
        return EventMapper.toEventFullDto(event,
                getConfirmedRequests(eventId), 0L);
    }

    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId,
                                          UpdateEventUserRequest request) {
        checkUserExists(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        if (event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException(
                    "Only pending or canceled events can be changed");
        }

        if (request.getEventDate() != null
                && request.getEventDate().isBefore(
                LocalDateTime.now().plusHours(2))) {
            throw new IllegalArgumentException(
                    "Event date must be at least 2 hours from now");
        }

        applyUserUpdate(event, request);
        return EventMapper.toEventFullDto(eventRepository.save(event),
                getConfirmedRequests(eventId), 0L);
    }

    public List<ParticipationRequestDto> getEventRequests(Long userId,
                                                          Long eventId) {
        checkUserExists(userId);
        return requestRepository.findAllByEventId(eventId).stream()
                .map(RequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventRequestStatusUpdateResult changeRequestStatus(
            Long userId, Long eventId,
            EventRequestStatusUpdateRequest request) {
        checkUserExists(userId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        List<ParticipationRequest> requests =
                requestRepository.findAllByIdIn(request.getRequestIds());

        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        for (ParticipationRequest req : requests) {
            if (!req.getStatus().equals(RequestStatus.PENDING)) {
                throw new ConflictException(
                        "Request must have status PENDING");
            }

            if (request.getStatus().equals(RequestStatus.CONFIRMED)) {
                Long confirmedCount = requestRepository
                        .countByEventIdAndStatus(eventId,
                                RequestStatus.CONFIRMED);
                if (event.getParticipantLimit() > 0
                        && confirmedCount >= event.getParticipantLimit()) {
                    throw new ConflictException(
                            "The participant limit has been reached");
                }
                req.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(RequestMapper.toDto(
                        requestRepository.save(req)));

                Long newCount = requestRepository.countByEventIdAndStatus(
                        eventId, RequestStatus.CONFIRMED);
                if (event.getParticipantLimit() > 0
                        && newCount >= event.getParticipantLimit()) {
                    List<ParticipationRequest> pending =
                            requestRepository.findAllByEventIdAndStatus(
                                    eventId, RequestStatus.PENDING);
                    for (ParticipationRequest p : pending) {
                        p.setStatus(RequestStatus.REJECTED);
                        rejected.add(RequestMapper.toDto(
                                requestRepository.save(p)));
                    }
                }
            } else {
                req.setStatus(RequestStatus.REJECTED);
                rejected.add(RequestMapper.toDto(requestRepository.save(req)));
            }
        }

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed)
                .rejectedRequests(rejected)
                .build();
    }

    public List<EventFullDto> getEventsByAdmin(List<Long> users,
                                               List<String> states,
                                               List<Long> categories,
                                               LocalDateTime rangeStart,
                                               LocalDateTime rangeEnd,
                                               int from, int size) {
        PageRequest page = PageRequest.of(from / size, size);
        Specification<Event> spec = buildAdminSpec(
                users, states, categories, rangeStart, rangeEnd);
        return eventRepository.findAll(spec, page).getContent().stream()
                .map(e -> EventMapper.toEventFullDto(e,
                        getConfirmedRequests(e.getId()), 0L))
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId,
                                           UpdateEventAdminRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        if (request.getStateAction() != null) {
            if (request.getStateAction().equals(AdminStateAction.PUBLISH_EVENT)) {
                if (!event.getState().equals(EventState.PENDING)) {
                    throw new ConflictException(
                            "Cannot publish the event because it's not "
                                    + "in the right state: "
                                    + event.getState());
                }
                if (request.getEventDate() != null
                        && request.getEventDate().isBefore(
                        LocalDateTime.now().plusHours(1))) {
                    throw new IllegalArgumentException(
                            "Event date must be at least 1 hour from now");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (request.getStateAction()
                    .equals(AdminStateAction.REJECT_EVENT)) {
                if (event.getState().equals(EventState.PUBLISHED)) {
                    throw new ConflictException(
                            "Cannot reject published event");
                }
                event.setState(EventState.CANCELED);
            }
        }

        applyAdminUpdate(event, request);
        return EventMapper.toEventFullDto(eventRepository.save(event),
                getConfirmedRequests(eventId), 0L);
    }

    public List<EventShortDto> getPublicEvents(String text,
                                               List<Long> categories,
                                               Boolean paid,
                                               LocalDateTime rangeStart,
                                               LocalDateTime rangeEnd,
                                               Boolean onlyAvailable,
                                               String sort,
                                               int from, int size,
                                               HttpServletRequest request) {
        statsClient.saveHit(APP_NAME, request.getRequestURI(),
                request.getRemoteAddr(), LocalDateTime.now());

        if (rangeStart != null && rangeEnd != null
                && rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException(
                    "rangeStart must be before rangeEnd");
        }

        PageRequest page = PageRequest.of(from / size, size);
        Specification<Event> spec = buildPublicSpec(
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable);

        List<Event> events = eventRepository.findAll(spec, page).getContent();

        List<EventShortDto> result = events.stream()
                .map(e -> EventMapper.toEventShortDto(e,
                        getConfirmedRequests(e.getId()),
                        getViews(e.getId())))
                .collect(Collectors.toList());

        if ("VIEWS".equals(sort)) {
            result.sort((a, b) -> Long.compare(
                    b.getViews() != null ? b.getViews() : 0,
                    a.getViews() != null ? a.getViews() : 0));
        }

        return result;
    }

    public EventFullDto getPublicEventById(Long eventId,
                                           HttpServletRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new NotFoundException(
                    "Event with id=" + eventId + " was not found");
        }

        statsClient.saveHit(APP_NAME, request.getRequestURI(),
                request.getRemoteAddr(), LocalDateTime.now());

        Long views = getViews(eventId);
        return EventMapper.toEventFullDto(event,
                getConfirmedRequests(eventId), views);
    }

    private Long getConfirmedRequests(Long eventId) {
        return requestRepository.countByEventIdAndStatus(
                eventId, RequestStatus.CONFIRMED);
    }

    private Long getViews(Long eventId) {
        String uri = "/events/" + eventId;
        try {
            var response = statsClient.getStats(
                    LocalDateTime.now().minusYears(10),
                    LocalDateTime.now().plusSeconds(1),
                    List.of(uri),
                    true);
            if (response.getBody() instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof java.util.LinkedHashMap<?, ?> map) {
                    Object hits = map.get("hits");
                    if (hits instanceof Number number) {
                        return number.longValue();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException(
                    "User with id=" + userId + " was not found");
        }
    }

    private void applyUserUpdate(Event event, UpdateEventUserRequest req) {
        if (req.getAnnotation() != null) event.setAnnotation(req.getAnnotation());
        if (req.getDescription() != null) {
            event.setDescription(req.getDescription());
        }
        if (req.getEventDate() != null) event.setEventDate(req.getEventDate());
        if (req.getPaid() != null) event.setPaid(req.getPaid());
        if (req.getParticipantLimit() != null) {
            event.setParticipantLimit(req.getParticipantLimit());
        }
        if (req.getRequestModeration() != null) {
            event.setRequestModeration(req.getRequestModeration());
        }
        if (req.getTitle() != null) event.setTitle(req.getTitle());
        if (req.getLocation() != null) {
            event.setLocation(new Location(req.getLocation().getLat(),
                    req.getLocation().getLon()));
        }
        if (req.getCategory() != null) {
            Category category = categoryRepository.findById(req.getCategory())
                    .orElseThrow(() -> new NotFoundException(
                            "Category not found"));
            event.setCategory(category);
        }
        if (req.getStateAction() != null) {
            if (req.getStateAction().equals(UserStateAction.SEND_TO_REVIEW)) {
                event.setState(EventState.PENDING);
            } else {
                event.setState(EventState.CANCELED);
            }
        }
    }

    private void applyAdminUpdate(Event event, UpdateEventAdminRequest req) {
        if (req.getAnnotation() != null) event.setAnnotation(req.getAnnotation());
        if (req.getDescription() != null) {
            event.setDescription(req.getDescription());
        }
        if (req.getEventDate() != null) event.setEventDate(req.getEventDate());
        if (req.getPaid() != null) event.setPaid(req.getPaid());
        if (req.getParticipantLimit() != null) {
            event.setParticipantLimit(req.getParticipantLimit());
        }
        if (req.getRequestModeration() != null) {
            event.setRequestModeration(req.getRequestModeration());
        }
        if (req.getTitle() != null) event.setTitle(req.getTitle());
        if (req.getLocation() != null) {
            event.setLocation(new Location(req.getLocation().getLat(),
                    req.getLocation().getLon()));
        }
        if (req.getCategory() != null) {
            Category category = categoryRepository.findById(req.getCategory())
                    .orElseThrow(() -> new NotFoundException(
                            "Category not found"));
            event.setCategory(category);
        }
    }

    private Specification<Event> buildPublicSpec(String text,
                                                 List<Long> categories,
                                                 Boolean paid,
                                                 LocalDateTime rangeStart,
                                                 LocalDateTime rangeEnd,
                                                 Boolean onlyAvailable) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("state"), EventState.PUBLISHED));

            if (text != null && !text.isBlank()) {
                String pattern = "%" + text.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("annotation")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)));
            }

            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categories));
            }

            if (paid != null) {
                predicates.add(cb.equal(root.get("paid"), paid));
            }

            LocalDateTime start = rangeStart != null
                    ? rangeStart : LocalDateTime.now();
            predicates.add(cb.greaterThan(root.get("eventDate"), start));

            if (rangeEnd != null) {
                predicates.add(cb.lessThan(root.get("eventDate"), rangeEnd));
            }

            return cb.and(predicates.toArray(
                    new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Specification<Event> buildAdminSpec(List<Long> users,
                                                List<String> states,
                                                List<Long> categories,
                                                LocalDateTime rangeStart,
                                                LocalDateTime rangeEnd) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<>();

            if (users != null && !users.isEmpty()) {
                predicates.add(root.get("initiator").get("id").in(users));
            }

            if (states != null && !states.isEmpty()) {
                List<EventState> eventStates = states.stream()
                        .map(EventState::valueOf)
                        .collect(Collectors.toList());
                predicates.add(root.get("state").in(eventStates));
            }

            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categories));
            }

            if (rangeStart != null) {
                predicates.add(cb.greaterThan(
                        root.get("eventDate"), rangeStart));
            }

            if (rangeEnd != null) {
                predicates.add(cb.lessThan(root.get("eventDate"), rangeEnd));
            }

            return cb.and(predicates.toArray(
                    new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}