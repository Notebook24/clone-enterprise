package com.orangeandbronze.enlistment.domain;

import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

import static com.orangeandbronze.enlistment.domain.Days.*;
import static com.orangeandbronze.enlistment.domain.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class StudentTest {

    private final static Period H0830 = new Period(LocalTime.of(8, 30), LocalTime.of(10, 0));

    @Test
    void enlist_two_sections_no_conflict() {
        // Given a student and two sections
        Student student = newDefaultStudent();
        Section sec1 = new SectionBuilder("A").subject("C").schedule(MTH830to10).room("X").build();
        Section sec2 = new SectionBuilder("B").subject("D").schedule(TF10to1130).room("Y").build();
        // When the student enlists in both sections
        student.enlist(sec1);
        student.enlist(sec2);
        // Then both sections should be found in the student & no other sections
        Collection<Section> sections = student.getSections();
        assertAll(
                () -> assertTrue(sections.containsAll(List.of(sec1, sec2))),
                () -> assertEquals(2, sections.size())
        );
    }

    @Test
    void enlist_two_sections_same_schedule() {
        // Given a student & two sections w/ same sked
        Student student = newDefaultStudent();
        Section sec1 = new SectionBuilder("A").subject("C").schedule(MTH830to10).room("X").build();
        Section sec2 = new SectionBuilder("B").subject("D").schedule(MTH830to10).room("Y").build();
        // When the student enlists in both sections
        student.enlist(sec1);
        // Then an exception should be thrown on the second enlistment
        assertThrows(ScheduleConflictException.class, () -> student.enlist(sec2));
    }

    @Test
    void enlist_within_room_capacity() {
        // Given two students and one section with room capacity 5
        Student student1 = newStudent(1);
        Student student2 = newStudent(2);
        Section section = newDefaultSection();
        // When the two students enlist in the section
        student1.enlist(section);
        student2.enlist(section);
        // Then the number for students in the section should be 2
        assertEquals(2, section.getNumberOfStudents());
    }

    @Test
    void enlist_exceeding_room_capacity() {
        // Given two students and one section with room capacity 1
        Student student1 = newDefaultStudent();
        Student student2 = newStudent(2);
        final int CAPACITY = 1;
        Section section = new SectionBuilder().room(CAPACITY).build();
        // When the two students enlist in the section
        student1.enlist(section);
        // Then an exception should be thrown at 2nd enlistment
        assertThrows(CapacityException.class, () -> student2.enlist(section));
    }

    @Test
    void enlist_students_at_capacity_in_two_sections_sharing_the_same_room() {
        // Given 2 sections that share same room w/ capacity 1, and 2 students
        final int CAPACITY = 1;
        Room room = newRoom(CAPACITY);
        Section sec1 = new SectionBuilder("A").subject("C").schedule(MTH830to10).room(room).build();
        Section sec2 = new SectionBuilder("B").subject("C").schedule(TF830to10).room(room).build();
        Student student1 = newStudent(1);
        Student student2 = newStudent(2);
        // When each student enlists in a different section
        student1.enlist(sec1);
        student2.enlist(sec2);
        // No exception should be thrown
    }

    @Test
    void cancel_enlisted_section() {
        // Given a student that has sections, which have students
        final int INITIAL_NUMBER_OF_STUDENTS = 5;
        Section sec1 = new SectionBuilder("A").subject("D").schedule(MTH830to10).room("X").noStudents(INITIAL_NUMBER_OF_STUDENTS).build();
        Section sec2 = new SectionBuilder("B").subject("E").schedule(TF830to10).room("Y").noStudents(INITIAL_NUMBER_OF_STUDENTS).build();
        Section sectionToBeCanceled = new SectionBuilder("C").subject("F").schedule(new Schedule(WS, H0830)).room("Z").noStudents(INITIAL_NUMBER_OF_STUDENTS).build();
        Student student = newStudent(1, List.of(sec1, sec2, sectionToBeCanceled));
        // When the student cancels one section
        student.cancel(sectionToBeCanceled);
        // Then the section will no longer be found with the student
        // and the number of students in the section removed will be decremented;
        assertAll(
                () -> assertFalse(student.getSections().contains(sectionToBeCanceled)),
                () -> {
                    final int DECREMENTED_NUMBER_OF_STUDENTS = 4;
                    assertEquals(DECREMENTED_NUMBER_OF_STUDENTS, sectionToBeCanceled.getNumberOfStudents());
                }
        );
    }

    @Test
    void cancel_nonenlisted_section() {
        // Given a student that has sections, which have students, and one section that the student hasn't enlisted in
        final int INITIAL_NUMBER_OF_STUDENTS = 5;
        Section sec1 = new SectionBuilder("A").subject("D").schedule(MTH830to10).room("X").noStudents(INITIAL_NUMBER_OF_STUDENTS).build();
        Section sec2 = new SectionBuilder("B").subject("E").schedule(TF830to10).room("Y").noStudents(INITIAL_NUMBER_OF_STUDENTS).build();
        Section sectionToBeCanceled = new SectionBuilder("C").subject("F").schedule(new Schedule(WS, H0830)).room("Z").noStudents(INITIAL_NUMBER_OF_STUDENTS).build();
        Student student = newStudent(1, List.of(sec1, sec2));
        // When a student cancels a section that the student hasn't enlisted in
        student.cancel(sectionToBeCanceled);
        // The system will do nothing, student's sections unchanged, number of students of sections unchanged
        Collection<Section> sections = student.getSections();
        assertAll(
                () -> assertTrue(sections.containsAll(List.of(sec1, sec2))),
                () -> assertFalse(sections.contains(sectionToBeCanceled)),
                () -> assertEquals(2, sections.size()),
                () -> assertEquals(INITIAL_NUMBER_OF_STUDENTS, sec1.getNumberOfStudents()),
                () -> assertEquals(INITIAL_NUMBER_OF_STUDENTS, sec2.getNumberOfStudents())
        );
    }

    @Test // happy path is enlist_two_sections_no_conflict()
    void enlist_two_sections_same_subject() {
        // Given student & 2 sections same subject
        Student student = newDefaultStudent();
        Section sec1 = new SectionBuilder("A").subject("C").schedule(MTH830to10).room("X", 10).build();
        Section sec2 = new SectionBuilder("B").subject("C").schedule(TF830to10).room("Y", 10).build();
        // When student enlists in both
        student.enlist(sec1);
        // Then exception should be thrown in 2nd
        assertThrows(SameSubjectException.class, () -> student.enlist(sec2));
    }

    @Test
    void enlist_section_prereqs_taken() {
        // Given section & student where prereqs taken
        Subject prereq1 = new Subject("prereq1");
        Subject prereq2 = new Subject("prereq2");
        Subject subject = new Subject("subject", List.of(prereq1, prereq2));
        Subject otherSubject = new Subject("otherSubject");
        List<Subject> subjectsTaken = List.of(prereq1, prereq2, otherSubject);
        Student student = newStudent(1, Collections.emptyList(), subjectsTaken);
        Section section = new SectionBuilder().subject(subject).build();
        // When student enlists
        student.enlist(section);
        // Then enlistment is successful
        assertAll(
                () -> assertTrue(student.getSections().contains(section)),
                () -> assertEquals(1, section.getNumberOfStudents())
        );
    }

    @Test
    void enlist_section_prereq_missing() {
        // Given section & student where some prereqs missing
        Subject prereq1 = new Subject("prereq1");
        Subject prereq2 = new Subject("prereq2");
        Subject prereq3 = new Subject("prereq3");
        Subject prereq4 = new Subject("prereq4");
        Subject subject = new Subject("subject", List.of(prereq1, prereq2, prereq3, prereq4));
        Subject otherSubject = new Subject("otherSubject");
        List<Subject> subjectsTaken = List.of(prereq1, prereq2, otherSubject);
        Student student = newStudent(1, Collections.emptyList(), subjectsTaken);
        Section section = new SectionBuilder().subject(subject).build();
        // When student enlists
        // Then exception thrown
        assertThrows(PrereqMissingException.class, () -> student.enlist(section));
    }


}
