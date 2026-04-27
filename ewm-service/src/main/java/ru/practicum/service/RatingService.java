package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EventRatingDto;
import ru.practicum.dto.MyRatingDto;
import ru.practicum.dto.UserRatingDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.RatingMapper;
import ru.practicum.model.Event;
import ru.practicum.model.EventRating;
import ru.practicum.model.EventState;
import ru.practicum.model.RequestStatus;
import ru.practicum.model.User;
import ru.practicum.repository.EventRatingRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.RequestRepository;
import ru.practicum.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingService {

    private final EventRatingRepository ratingRepository;

    private final EventRepository eventRepository;

    private final UserRepository userRepository;

    private final RequestRepository requestRepository;

    @Transactional
    public EventRatingDto rateEvent(Long userId, Long eventId,
                                    Boolean positive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "User with id=" + userId + " was not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Event must be published");
        }

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException(
                    "Initiator cannot rate own event");
        }

        boolean hasConfirmedRequest = requestRepository
                .findByRequesterIdAndEventId(userId, eventId)
                .map(r -> r.getStatus().equals(RequestStatus.CONFIRMED))
                .orElse(false);

        if (!hasConfirmedRequest) {
            throw new ConflictException(
                    "User must have confirmed participation to rate the event");
        }

        Optional<EventRating> existing =
                ratingRepository.findByEventIdAndUserId(eventId, userId);

        if (existing.isPresent()) {
            if (existing.get().getPositive().equals(positive)) {
                throw new ConflictException(
                        "You already rated this event with the same score");
            }
            existing.get().setPositive(positive);
            ratingRepository.save(existing.get());
        } else {
            EventRating rating = EventRating.builder()
                    .event(event)
                    .user(user)
                    .positive(positive)
                    .build();
            ratingRepository.save(rating);
        }

        return buildEventRatingDto(event);
    }

    @Transactional
    public void removeRating(Long userId, Long eventId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException(
                    "User with id=" + userId + " was not found");
        }
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException(
                    "Event with id=" + eventId + " was not found");
        }

        EventRating rating = ratingRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Rating not found"));
        ratingRepository.delete(rating);
    }

    public EventRatingDto getEventRating(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));
        return buildEventRatingDto(event);
    }

    public List<EventRatingDto> getTopEvents(int size) {
        PageRequest page = PageRequest.of(0, size);
        List<Object[]> rows = ratingRepository.findTopEventRatings(page);

        return rows.stream()
                .map(row -> EventRatingDto.builder()
                        .eventId(((Number) row[0]).longValue())
                        .eventTitle((String) row[1])
                        .likes(((Number) row[2]).longValue())
                        .dislikes(((Number) row[3]).longValue())
                        .rating(((Number) row[4]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    public List<UserRatingDto> getTopOrganizers(int size) {
        PageRequest page = PageRequest.of(0, size);
        List<Object[]> rows = ratingRepository.findTopOrganizerRatings(page);

        return rows.stream()
                .map(row -> UserRatingDto.builder()
                        .userId(((Number) row[0]).longValue())
                        .userName((String) row[1])
                        .totalRating(((Number) row[2]).longValue())
                        .eventsCount(((Number) row[3]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    public UserRatingDto getUserRating(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "User with id=" + userId + " was not found"));

        Long totalRating =
                ratingRepository.calculateTotalRatingByUserId(userId);
        Long eventsCount =
                ratingRepository.countRatedEventsByUserId(userId);

        return UserRatingDto.builder()
                .userId(user.getId())
                .userName(user.getName())
                .totalRating(totalRating)
                .eventsCount(eventsCount)
                .build();
    }

    public List<MyRatingDto> getMyRatings(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException(
                    "User with id=" + userId + " was not found");
        }

        return ratingRepository.findAllByUserId(userId).stream()
                .map(r -> MyRatingDto.builder()
                        .eventId(r.getEvent().getId())
                        .eventTitle(r.getEvent().getTitle())
                        .positive(r.getPositive())
                        .build())
                .collect(Collectors.toList());
    }

    private EventRatingDto buildEventRatingDto(Event event) {
        Long likes = ratingRepository
                .countByEventIdAndPositiveTrue(event.getId());
        Long dislikes = ratingRepository
                .countByEventIdAndPositiveFalse(event.getId());
        return RatingMapper.toEventRatingDto(event, likes, dislikes);
    }
}