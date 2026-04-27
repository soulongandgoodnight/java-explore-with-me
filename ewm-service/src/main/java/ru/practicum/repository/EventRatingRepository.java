package ru.practicum.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.EventRating;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface EventRatingRepository extends JpaRepository<EventRating, Long> {

    Optional<EventRating> findByEventIdAndUserId(Long eventId, Long userId);

    Long countByEventIdAndPositiveTrue(Long eventId);

    Long countByEventIdAndPositiveFalse(Long eventId);

    void deleteAllByUserId(Long userId);

    List<EventRating> findAllByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(CASE WHEN r.positive = true THEN 1 "
            + "WHEN r.positive = false THEN -1 ELSE 0 END), 0) "
            + "FROM EventRating r WHERE r.event.id = :eventId")
    Long calculateRatingByEventId(@Param("eventId") Long eventId);

    @Query("SELECT COALESCE(SUM(CASE WHEN r.positive = true THEN 1 "
            + "WHEN r.positive = false THEN -1 ELSE 0 END), 0) "
            + "FROM EventRating r WHERE r.event.initiator.id = :userId")
    Long calculateTotalRatingByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT r.event.id) "
            + "FROM EventRating r WHERE r.event.initiator.id = :userId")
    Long countRatedEventsByUserId(@Param("userId") Long userId);

    @Query("SELECT r.event.id AS eventId, "
            + "r.event.title AS eventTitle, "
            + "SUM(CASE WHEN r.positive = true THEN 1 ELSE 0 END) AS likes, "
            + "SUM(CASE WHEN r.positive = false THEN 1 ELSE 0 END) AS dislikes, "
            + "SUM(CASE WHEN r.positive = true THEN 1 "
            + "WHEN r.positive = false THEN -1 ELSE 0 END) AS rating "
            + "FROM EventRating r "
            + "WHERE r.event.state = ru.practicum.model.EventState.PUBLISHED "
            + "GROUP BY r.event.id, r.event.title "
            + "ORDER BY rating DESC, r.event.id ASC")
    List<Object[]> findTopEventRatings(Pageable pageable);

    @Query("SELECT r.event.initiator.id AS userId, "
            + "r.event.initiator.name AS userName, "
            + "SUM(CASE WHEN r.positive = true THEN 1 "
            + "WHEN r.positive = false THEN -1 ELSE 0 END) AS totalRating, "
            + "COUNT(DISTINCT r.event.id) AS eventsCount "
            + "FROM EventRating r "
            + "WHERE r.event.state = ru.practicum.model.EventState.PUBLISHED "
            + "GROUP BY r.event.initiator.id, r.event.initiator.name "
            + "ORDER BY totalRating DESC, r.event.initiator.id ASC")
    List<Object[]> findTopOrganizerRatings(Pageable pageable);

    @Query("SELECT r.event.id AS eventId, "
            + "SUM(CASE WHEN r.positive = true THEN 1 "
            + "WHEN r.positive = false THEN -1 ELSE 0 END) AS rating "
            + "FROM EventRating r "
            + "WHERE r.event.id IN :eventIds "
            + "GROUP BY r.event.id")
    List<Object[]> findRatingsByEventIds(
            @Param("eventIds") List<Long> eventIds);

    default Map<Long, Long> getRatingsMapByEventIds(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return findRatingsByEventIds(eventIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()
                ));
    }
}