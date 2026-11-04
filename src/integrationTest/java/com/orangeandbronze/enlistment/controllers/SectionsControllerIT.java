package com.orangeandbronze.enlistment.controllers;

import com.orangeandbronze.enlistment.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.testcontainers.junit.jupiter.*;

import java.time.*;
import java.util.*;

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
        // Given: Admin in session, but NO subject or room in DB yet
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

        // Then: Rows exist in section and room_sections
        assertAll(
                () -> {
                    Integer sectionCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM section WHERE section_id = ? " +
                                    "AND subject_subject_id = ? " +
                                    "AND days = ? " +
                                    "AND start_time = ? " +
                                    "AND end_time = ? " +
                                    "AND room_name = ?",
                            Integer.class, DEFAULT_SECTION_ID, DEFAULT_SUBJECT_ID,
                            Days.MTH.ordinal(),
                            LocalTime.of(8, 30),
                            LocalTime.of(10, 0),
                            DEFAULT_ROOM_NAME);
                    assertEquals(1, sectionCount, "section row does not exist with expected values");
                },
                () -> {
                    Integer roomSectionCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM room_sections WHERE room_name = ? " +
                                    "AND sections_section_id = ?",
                            Integer.class, DEFAULT_ROOM_NAME, DEFAULT_SECTION_ID);
                    assertEquals(1, roomSectionCount, "room_sections link does not exist");
                }
        );
    }

}
