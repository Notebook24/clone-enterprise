package com.orangeandbronze.enlistment.controllers;

import com.orangeandbronze.enlistment.domain.*;
import com.orangeandbronze.enlistment.repositories.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.jdbc.core.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static com.orangeandbronze.enlistment.controllers.UserAction.*;
import static com.orangeandbronze.enlistment.domain.TestUtils.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@SpringBootTest
class EnlistControllerIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    private final static String TEST = "test";

    @Container
    static final PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName(TEST).withUsername(TEST).withPassword(TEST);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Test
    void enlist_student_in_section() throws Exception {
        //Given: The DB has an existing student record & section record
        insertDefaultStudent();
        insertNewDefaultSectionWithCapacity(10);
        Student student = studentRepository.findById(DEFAULT_STUDENT_NUMBER).orElseThrow();

        //When: POST method on path "/enlist" is invoked with
            //parameters for sectionId matching the section record in the DB,
            //UserAction is "ENLIST", and student object in session corresponding
            //to the student record in the DB
        mockMvc.perform(post("/enlist").sessionAttr("student", student)
            .param("sectionId", DEFAULT_SECTION_ID).param("userAction", ENLIST.name()))
                .andExpect(status().is3xxRedirection());

        //Then: new record created in student_sections table,
            //containing corresponding studentNumber & sectionId
        int actualCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM student_sections 
            WHERE student_student_number = ? AND sections_section_id = ?""",
            Integer.class, DEFAULT_STUDENT_NUMBER, DEFAULT_SECTION_ID);
        assertEquals(1, actualCount);

    }


    @Test
    void cancel_student_in_section() throws Exception {

    }

    private final static int FIRST_STUDENT_NO = 11;
    private final static int NUMBER_OF_STUDENTS = 5;
    private final static int LAST_STUDENT_NUMBER = FIRST_STUDENT_NO + NUMBER_OF_STUDENTS - 1;

    private void insertDefaultStudent() {
        jdbcTemplate.update("INSERT INTO student (student_number, firstname, lastname) VALUES (?, ?, ?)",
                DEFAULT_STUDENT_NUMBER, "firstname", "lastname");
    }
    
    private void insertManyStudents() {
        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = FIRST_STUDENT_NO; i <= LAST_STUDENT_NUMBER; i++) {
            batchArgs.add(new Object[]{i, "firstname", "lastname"});
        }
        jdbcTemplate.batchUpdate("INSERT INTO student(student_number, firstname, lastname) VALUES (?, ?, ?)", batchArgs);
    }

    private void insertNewDefaultSectionWithCapacity(int capacity) {
        final String roomName = "roomName";
        jdbcTemplate.update("INSERT INTO room (name, capacity) VALUES (?, ?)", roomName, capacity);
        jdbcTemplate.update("INSERT INTO subject (subject_id) VALUES (?)", DEFAULT_SUBJECT_ID);
        jdbcTemplate.update(
                "INSERT INTO section (section_id, number_of_students, days, start_time, end_time, room_name, subject_subject_id, version)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                DEFAULT_SECTION_ID, 0, Days.MTH.ordinal(), LocalTime.of(9, 0), LocalTime.of(10, 0), roomName, DEFAULT_SUBJECT_ID, 0);
    }

    private void assertNumberOfStudentsSuccessfullyEnlistedInDefaultSection(int expectedCount) {
        assertAll(
                () -> {
                    int numStudentSectionAssociations = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM student_sections WHERE sections_section_id = ?", Integer.class, DEFAULT_SECTION_ID);
                    assertEquals(expectedCount, numStudentSectionAssociations, "student_sections wrong count");
                },
                () -> {
                    int numStudentsInSectionField = jdbcTemplate.queryForObject("SELECT number_of_students FROM section WHERE section_id = ?",
                            Integer.class, DEFAULT_SECTION_ID);
                    assertEquals(expectedCount, numStudentsInSectionField, "section.number_of_students wrong count");
                }
        );
    }

    @Test
    void enlist_concurrently_same_section_not_enough_capacity() throws Exception {
        //Given several students and a section with capacity of 1
        insertManyStudents();
        insertNewDefaultSectionWithCapacity(1);

        //When all students enlist at the same time
        startEnlistmentThreads();

        //Then only one student should be enlisted
        assertNumberOfStudentsSuccessfullyEnlistedInDefaultSection(1);
    }


    @Test
    void enlist_concurrently_same_section_enough_capacity() throws Exception {
        //Given several students and a section with enough capacity for all the students
        insertManyStudents();
        insertNewDefaultSectionWithCapacity(NUMBER_OF_STUDENTS);

        //When all those students enlist at the same time
        startEnlistmentThreads();

        //Then they should all get enlisted
        assertNumberOfStudentsSuccessfullyEnlistedInDefaultSection(NUMBER_OF_STUDENTS);
    }



    private void startEnlistmentThreads() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        for (int i = FIRST_STUDENT_NO; i <= LAST_STUDENT_NUMBER; i++) {
            final int studentNo = i;
            new EnlistmentThread(studentRepository.findById(studentNo).orElseThrow(() ->
                    new NoSuchElementException("No student w/ student num " + studentNo + " found in DB.")),
                    latch, mockMvc).start();
        }
        latch.countDown();
        Thread.sleep(5000); // wait time to allow all the threads to finish
    }

    private static class EnlistmentThread extends Thread {
        private final Student student;
        private final CountDownLatch latch;
        private final MockMvc mockMvc;

        public EnlistmentThread(Student student, CountDownLatch latch, MockMvc mockMvc) {
            this.student = student;
            this.latch = latch;
            this.mockMvc = mockMvc;
        }

        @Override
        public void run() {
            try {
                latch.await(); // The thread keeps waiting till it is informed
                mockMvc.perform(post("/enlist").sessionAttr("student", student)
                        .param("sectionId", DEFAULT_SECTION_ID).param("userAction", ENLIST.name()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
