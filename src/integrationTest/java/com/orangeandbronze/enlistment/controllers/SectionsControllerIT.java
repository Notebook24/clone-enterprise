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
class SectionsControllerIT  {

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
        // Given: Subject and Room exist in DB, and an admin is in session
        insertDefaultSubject();
        insertDefaultRoom();
        Admin admin = mock(Admin.class);

        // When: POST /sections with valid parameters
        mockMvc.perform(post("/sections")
                        .sessionAttr("admin", admin)
                        .param("sectionId", DEFAULT_SECTION_ID)
                        .param("subjectId", DEFAULT_SUBJECT_ID)
                        .param("days", Days.MTH.name())
                        .param("start", "08:30")
                        .param("end", "10:00")
                        .param("roomName", DEFAULT_ROOM_NAME))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("sections"))
                .andExpect(flash().attribute("sectionSuccessMessage", "Successfully created new section " + DEFAULT_SECTION_ID));

        // Then: Both section table and room_sections join table are verified
        assertSectionAndRoomSectionExist(DEFAULT_SECTION_ID,
                DEFAULT_SUBJECT_ID,
                Days.MTH,
                LocalTime.of(8, 30),
                LocalTime.of(10, 0),
                DEFAULT_ROOM_NAME);
    }

    @Test
    void createSection_overlaps_schedule() throws Exception {
        // Given: Subject, Room, and existing section occupies 8:30 - 10:00 on MTH in a room
        insertDefaultSubject();
        insertDefaultRoom();
        insertDefaultSection();

        Admin admin = mock(Admin.class);
        String conflictingSectionId = "ConflictingSection";
        int countBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM section", Integer.class);

        // When: POST /sections with overlapping schedule in same room (9:00 - 10:30)
        mockMvc.perform(post("/sections")
                        .sessionAttr("admin", admin)
                        .param("sectionId", conflictingSectionId)
                        .param("subjectId", DEFAULT_SUBJECT_ID)
                        .param("days", Days.MTH.name())
                        .param("start", "09:00")
                        .param("end", "10:30")
                        .param("roomName", DEFAULT_ROOM_NAME))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("sections"))
                .andExpect(flash().attributeExists("sectionExceptionMessage"));

        // Then: Conflicting section not saved, and total section count remains unchanged
        assertAll(
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM section WHERE section_id = ?", Integer.class, conflictingSectionId);
                    assertEquals(0, count, "conflicting section was unexpectedly saved to section table");
                },
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM room_sections WHERE sections_section_id = ?", Integer.class, conflictingSectionId);
                    assertEquals(0, count, "conflicting section was unexpectedly linked in room_sections");
                },
                () -> {
                    Integer countAfter = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM section", Integer.class);
                    assertEquals(countBefore, countAfter, "section table count should remain unchanged");
                }
        );
    }

    @Test
    void createSection_concurrently_same_room_overlapping_schedule() throws Exception {
        // Given: Subject and Room exist in DB
        insertDefaultSubject();
        insertDefaultRoom();
        Admin admin = mock(Admin.class);
        final int THREADS = 5;

        // When: N threads create sections with different IDs and overlapping schedules in the same room
        startSectionThreads(THREADS, i -> {
            try {
                mockMvc.perform(post("/sections")
                        .sessionAttr("admin", admin)
                        .param("sectionId", "SEC" + i)
                        .param("subjectId", DEFAULT_SUBJECT_ID)
                        .param("days", Days.MTH.name())
                        .param("start", "08:30")
                        .param("end", "10:00")
                        .param("roomName", DEFAULT_ROOM_NAME));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Then: exactly one section is saved in the room
        assertAll(
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM section WHERE room_name = ?", Integer.class, DEFAULT_ROOM_NAME);
                    assertEquals(1, count, "room should only have 1 section saved");
                },
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM room_sections WHERE room_name = ?", Integer.class, DEFAULT_ROOM_NAME);
                    assertEquals(1, count, "room_sections should only have 1 link");
                }
        );
    }

    @Test
    void createSection_concurrently_same_id() throws Exception {
        // Given: Multiple subjects and rooms in DB
        final int THREADS = 5;
        for (int i = 0; i < THREADS; i++) {
            jdbcTemplate.update("INSERT INTO subject (subject_id) VALUES (?)", "SUBJ" + i);
            jdbcTemplate.update("INSERT INTO room (name, capacity) VALUES (?, ?)", "ROOM" + i, 10);
        }
        Admin admin = mock(Admin.class);
        final String duplicateId = "DUPLICATEID";

        // When: N threads concurrently create a section with the same ID (each with their own room/subject)
        startSectionThreads(THREADS, i -> {
            try {
                mockMvc.perform(post("/sections")
                        .sessionAttr("admin", admin)
                        .param("sectionId", duplicateId)
                        .param("subjectId", "SUBJ" + i)
                        .param("days", Days.MTH.name())
                        .param("start", "08:30")
                        .param("end", "10:00")
                        .param("roomName", "ROOM" + i));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Then: exactly one section exists for that ID, and its fields belong to one complete request
        assertAll(
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM section WHERE section_id = ?", Integer.class, duplicateId);
                    assertEquals(1, count, "only one section should exist for the ID");
                },
                () -> {
                    Map<String, Object> row = jdbcTemplate.queryForMap(
                            "SELECT subject_subject_id, room_name FROM section WHERE section_id = ?", duplicateId);
                    String subj = (String) row.get("subject_subject_id");
                    String room = (String) row.get("room_name");
                    assertEquals(subj.replace("SUBJ", ""), room.replace("ROOM", ""),
                            "data must belong to one request (no mixed fields)");
                },
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM room_sections WHERE sections_section_id = ?", Integer.class, duplicateId);
                    assertEquals(1, count, "only one room_sections link should exist for the ID");
                }
        );
    }

    @Test
    void createSection_concurrently_same_room_non_overlapping_schedule() throws Exception {
        // Given: Subject and Room in DB
        insertDefaultSubject();
        insertDefaultRoom();
        Admin admin = mock(Admin.class);
        String[][] slots = {
                {"08:30", "10:00"},
                {"10:00", "11:30"},
                {"11:30", "13:00"}
        };
        final int THREADS = slots.length;

        // When: N threads concurrently create sections with non-overlapping schedules in the same room
        startSectionThreads(THREADS, i -> {
            try {
                mockMvc.perform(post("/sections")
                        .sessionAttr("admin", admin)
                        .param("sectionId", "NONOVERLAP" + i)
                        .param("subjectId", DEFAULT_SUBJECT_ID)
                        .param("days", Days.MTH.name())
                        .param("start", slots[i][0])
                        .param("end", slots[i][1])
                        .param("roomName", DEFAULT_ROOM_NAME));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Then: all N sections are saved in the room
        assertAll(
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM section WHERE room_name = ?", Integer.class, DEFAULT_ROOM_NAME);
                    assertEquals(THREADS, count, "all valid non-overlapping sections should be saved");
                },
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM room_sections WHERE room_name = ?", Integer.class, DEFAULT_ROOM_NAME);
                    assertEquals(THREADS, count, "room_sections must link all created sections");
                }
        );
    }

    private void insertDefaultSubject() {
        jdbcTemplate.update("INSERT INTO subject (subject_id) VALUES (?)", DEFAULT_SUBJECT_ID);
    }

    private void insertDefaultRoom() {
        jdbcTemplate.update("INSERT INTO room (name, capacity) VALUES (?, ?)", DEFAULT_ROOM_NAME, 10);
    }

    private void insertDefaultSection() {
        jdbcTemplate.update(
                "INSERT INTO section (section_id, " +
                        "number_of_students, " +
                        "days, " +
                        "start_time, " +
                        "end_time, " +
                        "room_name, " +
                        "subject_subject_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                DEFAULT_SECTION_ID, 0, Days.MTH.ordinal(),
                LocalTime.of(8, 30), LocalTime.of(10, 0),
                DEFAULT_ROOM_NAME, DEFAULT_SUBJECT_ID);

        jdbcTemplate.update(
                "INSERT INTO room_sections (room_name, sections_section_id) VALUES (?, ?)",
                DEFAULT_ROOM_NAME, DEFAULT_SECTION_ID);
    }

    private void assertSectionAndRoomSectionExist(String sectionId, String subjectId, Days days,
                                                  LocalTime start, LocalTime end, String roomName) {
        assertAll(
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM section WHERE section_id = ? " +
                                    "AND subject_subject_id = ? " +
                                    "AND days = ? " +
                                    "AND start_time = ? " +
                                    "AND end_time = ? " +
                                    "AND room_name = ?",
                            Integer.class, sectionId, subjectId, days.ordinal(), start, end, roomName);
                    assertEquals(1, count, "section table row does not exist with expected values");
                },
                () -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM room_sections WHERE room_name = ? AND sections_section_id = ?",
                            Integer.class, roomName, sectionId);
                    assertEquals(1, count, "room_sections link does not exist");
                }
        );
    }

    private void startSectionThreads(int threadCount, IntConsumer action) throws Exception {
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
                    f.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException ignored) {
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

}
