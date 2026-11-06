package com.orangeandbronze.enlistment.controllers;

import com.orangeandbronze.enlistment.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.testcontainers.junit.jupiter.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import static com.orangeandbronze.enlistment.domain.Days.MTH;
import static com.orangeandbronze.enlistment.domain.TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@SpringBootTest
class SectionsControllerIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;
 
    @Autowired
    private MockMvc mockMvc;
 
    private final static String TEST = "test";
 
    @Container
    private static final PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName(TEST).withUsername(TEST).withPassword(TEST);
 
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Test
    void createSection_save_to_db() throws Exception {
        // Given a subject, a room, and an admin session
        givenSubject(DEFAULT_SUBJECT_ID);
        givenRoom(DEFAULT_ROOM_NAME, DEFAULT_ROOM_CAPACITY);
        Admin admin = mock(Admin.class);

        // When the admin creates a section with a valid schedule
        createSectionRequest(admin, DEFAULT_SECTION_ID, DEFAULT_SUBJECT_ID, MTH,
                TIME_0830, TIME_1000, DEFAULT_ROOM_NAME)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("sections"))
                .andExpect(flash().attribute("sectionSuccessMessage", "Successfully created new section " + DEFAULT_SECTION_ID));

        // Then the section has the requested details and is linked to its room
        assertSectionDetails(DEFAULT_SECTION_ID, DEFAULT_SUBJECT_ID, MTH, TIME_0830, TIME_1000, DEFAULT_ROOM_NAME);
        assertRoomHasSections(DEFAULT_ROOM_NAME, DEFAULT_SECTION_ID);
    }

    @Test
    void createSection_overlaps_schedule() throws Exception {
        // Given a section occupying the room from 08:30 to 10:00 on MTH
        final String conflictingSectionId = "ConflictingSection";
        givenSubject(DEFAULT_SUBJECT_ID);
        givenRoom(DEFAULT_ROOM_NAME, DEFAULT_ROOM_CAPACITY);
        givenExistingSection();
        Admin admin = mock(Admin.class);
        int countBefore = sectionCount();

        // When the admin requests an overlapping section in the same room
        createSectionRequest(admin, conflictingSectionId, DEFAULT_SUBJECT_ID, MTH,
                TIME_0900, LocalTime.of(10, 30), DEFAULT_ROOM_NAME)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("sections"))
                .andExpect(flash().attributeExists("sectionExceptionMessage"));

        // Then the conflicting section is absent and the section count is unchanged
        assertAll(
                () -> assertSectionAbsent(conflictingSectionId),
                () -> assertSectionCount(countBefore)
        );
    }

    @Test
    void createSection_concurrently_same_room_overlapping_schedule() throws Exception {
        // Given a subject and a room available to several section requests
        final int numberOfRequests = 5;
        givenSubject(DEFAULT_SUBJECT_ID);
        givenRoom(DEFAULT_ROOM_NAME, DEFAULT_ROOM_CAPACITY);
        Admin admin = mock(Admin.class);

        // When different section IDs request the same schedule in the same room concurrently
        whenSectionsCreatedConcurrently(numberOfRequests, i -> {
            try {
                createSectionRequest(admin, "SEC" + i, DEFAULT_SUBJECT_ID, MTH,
                        TIME_0830, TIME_1000, DEFAULT_ROOM_NAME);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Then the room contains exactly one section and one association
        assertRoomSectionCount(DEFAULT_ROOM_NAME, 1);
    }

    @Test
    void createSection_concurrently_same_id() throws Exception {
        // Given distinct subject and room pairs for requests sharing one section ID
        final String duplicateId = "DUPLICATEID";
        final int numberOfRequests = 5;
        givenSubjectAndRoomPairs(numberOfRequests);
        Admin admin = mock(Admin.class);

        // When each pair requests the same section ID concurrently
        whenSectionsCreatedConcurrently(numberOfRequests, i -> {
            try {
                createSectionRequest(admin, duplicateId, "SUBJ" + i, MTH,
                        TIME_0830, TIME_1000, "ROOM" + i);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Then one complete request supplies the saved section and its room association
        assertSectionCreatedFromOneRequest(duplicateId);
    }

    @Test
    void createSection_concurrently_same_room_non_overlapping_schedule() throws Exception {
        // Given one room and adjacent schedules with distinct section IDs
        givenSubject(DEFAULT_SUBJECT_ID);
        givenRoom(DEFAULT_ROOM_NAME, DEFAULT_ROOM_CAPACITY);
        Admin admin = mock(Admin.class);
        LocalTime[][] slots = {
                {TIME_0830, TIME_1000},
                {TIME_1000, LocalTime.of(11, 30)},
                {LocalTime.of(11, 30), LocalTime.of(13, 0)}
        };
        String[] sectionIds = IntStream.range(0, slots.length)
                .mapToObj(i -> "NONOVERLAP" + i).toArray(String[]::new);

        // When all non-overlapping sections are created in the room concurrently
        whenSectionsCreatedConcurrently(slots.length, i -> {
            try {
                createSectionRequest(admin, sectionIds[i], DEFAULT_SUBJECT_ID, MTH,
                        slots[i][0], slots[i][1], DEFAULT_ROOM_NAME);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Then every requested section is stored and linked to the room
        assertRoomHasSections(DEFAULT_ROOM_NAME, sectionIds);
    }

    /** Inserts a subject needed by the section creation requests. */
    private void givenSubject(String subjectId) {
        jdbcTemplate.update("INSERT INTO subject (subject_id) VALUES (?)", subjectId);
    }

    /** Inserts a room with the capacity required by the scenario. */
    private void givenRoom(String roomName, int capacity) {
        jdbcTemplate.update("INSERT INTO room (name, capacity) VALUES (?, ?)", roomName, capacity);
    }

    /** Creates subject and room pairs with matching suffixes to identify the winning request. */
    private void givenSubjectAndRoomPairs(int count) {
        for (int i = 0; i < count; i++) {
            givenSubject("SUBJ" + i);
            givenRoom("ROOM" + i, DEFAULT_ROOM_CAPACITY);
        }
    }

    /** Seeds the default section and its room association; the subject and room must already exist. */
    private void givenExistingSection() {
        jdbcTemplate.update(
                "INSERT INTO section (section_id, number_of_students, days, start_time, end_time, room_name, subject_subject_id)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?)",
                DEFAULT_SECTION_ID, 0, MTH.ordinal(), TIME_0830, TIME_1000,
                DEFAULT_ROOM_NAME, DEFAULT_SUBJECT_ID);
        jdbcTemplate.update("INSERT INTO room_sections (room_name, sections_section_id) VALUES (?, ?)",
                DEFAULT_ROOM_NAME, DEFAULT_SECTION_ID);
    }

    /** Sends a section creation request with an admin session, leaving response assertions to the caller. */
    private ResultActions createSectionRequest(Admin admin, String sectionId, String subjectId, Days days,
                                               LocalTime start, LocalTime end, String roomName) throws Exception {
        return mockMvc.perform(post("/sections")
                .sessionAttr("admin", admin)
                .param("sectionId", sectionId)
                .param("subjectId", subjectId)
                .param("days", days.name())
                .param("start", start.toString())
                .param("end", end.toString())
                .param("roomName", roomName));
    }

    /** Checks the saved section's fields and its association with the expected room. */
    private void assertSectionDetails(String sectionId, String subjectId, Days days,
                                      LocalTime start, LocalTime end, String roomName) {
        assertAll(
                () -> {
                    int count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM section WHERE section_id = ? AND subject_subject_id = ? " +
                                    "AND days = ? AND start_time = ? AND end_time = ? AND room_name = ?",
                            Integer.class, sectionId, subjectId, days.ordinal(), start, end, roomName);
                    assertEquals(1, count,
                            "section table row does not exist with expected values");
                },
                () -> {
                    int count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM room_sections WHERE room_name = ? AND sections_section_id = ?",
                            Integer.class, roomName, sectionId);
                    assertEquals(1, count, "room_sections link does not exist");
                }
        );
    }

    /** Checks both room relationships contain exactly the expected section IDs, regardless of order. */
    private void assertRoomHasSections(String roomName, String... sectionIds) {
        List<String> expected = Arrays.stream(sectionIds).sorted().toList();
        assertAll(
                () -> assertEquals(expected, jdbcTemplate.queryForList(
                        "SELECT section_id FROM section WHERE room_name = ? ORDER BY section_id", String.class, roomName),
                        "all valid non-overlapping sections should be saved"),
                () -> assertEquals(expected, jdbcTemplate.queryForList(
                        "SELECT sections_section_id FROM room_sections WHERE room_name = ? ORDER BY sections_section_id",
                        String.class, roomName), "room_sections must link all created sections")
        );
    }

    /** Checks both room relationship counts when the winning section ID is unknown. */
    private void assertRoomSectionCount(String roomName, int expected) {
        assertAll(
                () -> assertEquals(expected, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM section WHERE room_name = ?", Integer.class, roomName),
                        "room should only have 1 section saved"),
                () -> assertEquals(expected, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_sections WHERE room_name = ?", Integer.class, roomName),
                        "room_sections should only have 1 link")
        );
    }

    /** Checks that neither a section record nor a room association exists for the rejected ID. */
    private void assertSectionAbsent(String sectionId) {
        assertAll(
                () -> assertEquals(0, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM section WHERE section_id = ?", Integer.class, sectionId),
                        "conflicting section was unexpectedly saved to section table"),
                () -> assertEquals(0, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_sections WHERE sections_section_id = ?", Integer.class, sectionId),
                        "conflicting section was unexpectedly linked in room_sections")
        );
    }

    /** Includes seeded sections so tests can compare totals before and after a rejected request. */
    private int sectionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM section", Integer.class);
    }

    /** Checks that a rejected request leaves the total number of sections unchanged. */
    private void assertSectionCount(int expected) {
        assertEquals(expected, sectionCount(), "section table count should remain unchanged");
    }

    /** Checks one saved section and room link, with its subject and room belonging to the same request. */
    private void assertSectionCreatedFromOneRequest(String sectionId) {
        assertAll(
                () -> assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM section WHERE section_id = ?", Integer.class, sectionId),
                        "only one section should exist for the ID"),
                () -> {
                    Map<String, Object> row = jdbcTemplate.queryForMap(
                            "SELECT subject_subject_id, room_name FROM section WHERE section_id = ?", sectionId);
                    String subjectId = (String) row.get("subject_subject_id");
                    String roomName = (String) row.get("room_name");
                    assertEquals(subjectId.replace("SUBJ", ""), roomName.replace("ROOM", ""),
                            "data must belong to one request (no mixed fields)");
                },
                () -> assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_sections WHERE sections_section_id = ?", Integer.class, sectionId),
                        "only one room_sections link should exist for the ID")
        );
    }

    /** Starts section requests together and waits for completion; worker exceptions are ignored. */
    private void whenSectionsCreatedConcurrently(int threadCount, IntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    latch.await();
                    action.accept(index);
                    return null;
                }));
            }
            latch.countDown();
            for (Future<?> f : futures) {
                try {
                    f.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException ignored) {
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

}
