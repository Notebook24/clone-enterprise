package com.orangeandbronze.enlistment.controllers;

import com.orangeandbronze.enlistment.domain.*;
import com.orangeandbronze.enlistment.repositories.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.*;
import org.springframework.test.web.servlet.*;

import java.util.*;
import java.util.concurrent.*;

import static com.orangeandbronze.enlistment.controllers.UserAction.*;
import static com.orangeandbronze.enlistment.domain.TestUtils.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


class EnlistControllerIT extends ControllerIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    private static final int NUMBER_OF_STUDENTS = 5;

    @Test
    void enlist_student_in_section() throws Exception {
        // Given a student and a section with available capacity
        Student student = givenStudent(DEFAULT_STUDENT_NUMBER);
        givenSectionWithCapacity(DEFAULT_ROOM_CAPACITY);

        // When the student enlists in the section
        enlistRequest(student, DEFAULT_SECTION_ID, ENLIST)
                .andExpect(status().is3xxRedirection());

        // Then the student is enrolled and the section records one student
        assertStudentEnrollment(DEFAULT_STUDENT_NUMBER, DEFAULT_SECTION_ID, 1);
        assertSectionEnrollment(DEFAULT_SECTION_ID, 1);
    }

    @Test
    void cancel_student_in_section() throws Exception {
        // Given a student enrolled in a section
        givenSectionWithCapacity(DEFAULT_ROOM_CAPACITY);
        Student student = givenStudentEnlistedIn(DEFAULT_SECTION_ID);

        // When the student cancels enrollment
        enlistRequest(student, DEFAULT_SECTION_ID, CANCEL)
                .andExpect(status().is3xxRedirection());

        // Then the enrollment is removed and the section is empty
        assertStudentEnrollment(DEFAULT_STUDENT_NUMBER, DEFAULT_SECTION_ID, 0);
        assertSectionEnrollment(DEFAULT_SECTION_ID, 0);
    }

    @Test
    void enlist_concurrently_same_section_not_enough_capacity() throws Exception {
        // Given several students competing for a section with one available place
        List<Student> students = givenStudents();
        givenSectionWithCapacity(1);

        // When all students enlist concurrently
        whenStudentsEnlistConcurrently(DEFAULT_SECTION_ID, students);

        // Then only one student is enrolled
        assertSectionEnrollment(DEFAULT_SECTION_ID, 1);
    }

    @Test
    void enlist_concurrently_same_section_enough_capacity() throws Exception {
        // Given a section with enough capacity for every student
        List<Student> students = givenStudents();
        givenSectionWithCapacity(NUMBER_OF_STUDENTS);

        // When all students enlist concurrently
        whenStudentsEnlistConcurrently(DEFAULT_SECTION_ID, students);

        // Then every student is enrolled
        assertSectionEnrollment(DEFAULT_SECTION_ID, NUMBER_OF_STUDENTS);
    }

    /** Inserts a student and loads it for use as the session student. */
    private Student givenStudent(int studentNumber) {
        jdbcTemplate.update("INSERT INTO student (student_number, firstname, lastname) VALUES (?, ?, ?)",
                studentNumber, "firstname", "lastname");
        return studentRepository.findById(studentNumber).orElseThrow();
    }

    /** Inserts the students used in concurrent tests and loads them before any requests start. */
    private List<Student> givenStudents() {
        final int firstStudentNumber = 11;
        final int lastStudentNumber = firstStudentNumber + NUMBER_OF_STUDENTS - 1;
        List<Object[]> batchArgs = new ArrayList<>();
        for (int studentNumber = firstStudentNumber; studentNumber <= lastStudentNumber; studentNumber++) {
            batchArgs.add(new Object[]{studentNumber, "firstname", "lastname"});
        }
        jdbcTemplate.batchUpdate("INSERT INTO student(student_number, firstname, lastname) VALUES (?, ?, ?)", batchArgs);

        List<Student> students = new ArrayList<>();
        for (int studentNumber = firstStudentNumber; studentNumber <= lastStudentNumber; studentNumber++) {
            students.add(studentRepository.findById(studentNumber).orElseThrow());
        }
        return students;
    }

    /** Creates an empty default section with its subject and a room of the given capacity. */
    private void givenSectionWithCapacity(int capacity) {
        final String roomName = "roomName";
        jdbcTemplate.update("INSERT INTO room (name, capacity) VALUES (?, ?)", roomName, capacity);
        jdbcTemplate.update("INSERT INTO subject (subject_id) VALUES (?)", DEFAULT_SUBJECT_ID);
        jdbcTemplate.update(
                "INSERT INTO section (section_id, number_of_students, days, start_time, end_time, room_name, subject_subject_id)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?)",
                DEFAULT_SECTION_ID, 0, Days.MTH.ordinal(), TIME_0900, TIME_1000,
                roomName, DEFAULT_SUBJECT_ID);
    }

    /** Seeds one enrollment and its stored count in an existing section, then loads the session student. */
    private Student givenStudentEnlistedIn(String sectionId) {
        givenStudent(DEFAULT_STUDENT_NUMBER);
        jdbcTemplate.update("UPDATE section SET number_of_students = ? WHERE section_id = ?",
                1, sectionId);
        jdbcTemplate.update(
                "INSERT INTO student_sections (student_student_number, sections_section_id) VALUES (?, ?)",
                DEFAULT_STUDENT_NUMBER, sectionId);
        return studentRepository.findById(DEFAULT_STUDENT_NUMBER).orElseThrow();
    }

    /** Sends an enlist or cancel request with the student in session, leaving response assertions to the caller. */
    private ResultActions enlistRequest(Student student, String sectionId, UserAction action) throws Exception {
        return mockMvc.perform(post("/enlist").sessionAttr("student", student)
                .param("sectionId", sectionId).param("userAction", action.name()));
    }

    /** Checks both enrollment links and the section's stored count against the expected total. */
    private void assertSectionEnrollment(String sectionId, int expected) {
        assertAll(
                () -> {
                    int associationCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM student_sections WHERE sections_section_id = ?", Integer.class, sectionId);
                    assertEquals(expected, associationCount, "student_sections wrong count");
                },
                () -> {
                    int storedCount = jdbcTemplate.queryForObject(
                            "SELECT number_of_students FROM section WHERE section_id = ?", Integer.class, sectionId);
                    assertEquals(expected, storedCount, "section.number_of_students wrong count");
                }
        );
    }

    /** Checks the number of enrollment rows for the specified student and section. */
    private void assertStudentEnrollment(int studentNumber, String sectionId, int expected) {
        int associationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_sections WHERE student_student_number = ? AND sections_section_id = ?",
                Integer.class, studentNumber, sectionId);
        assertEquals(expected, associationCount,
                "student_sections wrong count");
    }

    /** Starts enlistments together and waits for completion; worker exceptions are ignored. */
    private void whenStudentsEnlistConcurrently(String sectionId, List<Student> students) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(students.size());
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Student student : students) {
                futures.add(pool.submit(() -> {
                    latch.await();
                    return enlistRequest(student, sectionId, ENLIST);
                }));
            }
            latch.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    // A capacity rejection may surface as an exception; the DB assertions decide pass/fail.
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

}
