package com.orangeandbronze.enlistment.controllers;

import com.orangeandbronze.enlistment.domain.DuplicateSectionException;
import com.orangeandbronze.enlistment.domain.EnlistmentException;
import com.orangeandbronze.enlistment.domain.Room;
import com.orangeandbronze.enlistment.domain.Section;
import com.orangeandbronze.enlistment.repositories.AdminRepository;
import com.orangeandbronze.enlistment.repositories.RoomRepository;
import com.orangeandbronze.enlistment.repositories.SectionRepository;
import com.orangeandbronze.enlistment.repositories.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.NoSuchElementException;
import java.util.Optional;

import static com.orangeandbronze.enlistment.domain.Days.MTH;
import static com.orangeandbronze.enlistment.domain.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectionsControllerTest {

    static final String START = "08:30";
    static final String END = "10:00";

    @Mock SubjectRepository subjectRepo;
    @Mock AdminRepository adminRepo;
    @Mock RoomRepository roomRepo;
    @Mock SectionRepository sectionRepo;
    @Mock RedirectAttributes redirectAttrs;

    SectionsController controller;

    @BeforeEach
    void setUp() {
        controller = new SectionsController(subjectRepo, adminRepo, roomRepo, sectionRepo);
    }

    @Test
    void createSection_save_new_section_to_repository() {
        // Given a subject and a room that exist in their repositories
        Room room = newDefaultRoom();
        when(subjectRepo.findById(DEFAULT_SUBJECT_ID)).thenReturn(Optional.of(DEFAULT_SUBJECT));
        when(roomRepo.findByIdForUpdate(DEFAULT_ROOM_NAME)).thenReturn(Optional.of(room));

        // When the controller receives valid arguments
        controller.createSection(DEFAULT_SECTION_ID, DEFAULT_SUBJECT_ID, MTH,
                START, END, DEFAULT_ROOM_NAME, redirectAttrs);

        // Then the saved section has the given id, subject, schedule and room
        ArgumentCaptor<Section> captor = ArgumentCaptor.forClass(Section.class);
        verify(sectionRepo).save(captor.capture());
        Section saved = captor.getValue();
        assertAll(
                () -> assertEquals(DEFAULT_SECTION_ID, saved.getSectionId()),
                () -> assertSame(DEFAULT_SUBJECT, saved.getSubject()),
                () -> assertEquals(MTH830to10, saved.getSchedule()),
                () -> assertSame(room, saved.getRoom())
        );
    }

    @Test
    void createSection_sets_success_flash_message_and_redirects() {
        // Given a subject and a room that exist
        when(subjectRepo.findById(DEFAULT_SUBJECT_ID)).thenReturn(Optional.of(DEFAULT_SUBJECT));
        when(roomRepo.findByIdForUpdate(DEFAULT_ROOM_NAME)).thenReturn(Optional.of(newDefaultRoom()));

        // When the section is created
        String view = controller.createSection(DEFAULT_SECTION_ID, DEFAULT_SUBJECT_ID, MTH,
                START, END, DEFAULT_ROOM_NAME, redirectAttrs);

        // Then a success message is flashed and the admin is redirected to the GET method
        verify(redirectAttrs).addFlashAttribute("sectionSuccessMessage",
                "Successfully created new section " + DEFAULT_SECTION_ID);
        assertEquals("redirect:sections", view);
    }

    @Test
    void createSection_with_unknown_subject_throws_and_saves_nothing() {
        // Given a subject id that is not in the repository
        // (the mock returns an empty Optional by default)

        // When a section is created with it
        NoSuchElementException exception = assertThrows(NoSuchElementException.class,
                () -> controller.createSection(DEFAULT_SECTION_ID, "missingSubject", MTH,
                        START, END, DEFAULT_ROOM_NAME, redirectAttrs));

        // Then the message names the missing id and nothing is saved
        assertTrue(exception.getMessage().contains("missingSubject"));
        verify(sectionRepo, never()).save(any());
    }

    @Test
    void createSection_with_unknown_room_throws_and_saves_nothing() {
        // Given a subject that exists and a room name that does not
        when(subjectRepo.findById(DEFAULT_SUBJECT_ID)).thenReturn(Optional.of(DEFAULT_SUBJECT));

        // When a section is created with that room
        NoSuchElementException exception = assertThrows(NoSuchElementException.class,
                () -> controller.createSection(DEFAULT_SECTION_ID, DEFAULT_SUBJECT_ID, MTH,
                        START, END, "missingRoom", redirectAttrs));

        // Then the message names the missing room and nothing is saved
        assertTrue(exception.getMessage().contains("missingRoom"));
        verify(sectionRepo, never()).save(any());
    }

    @Test
    void createSection_with_room_schedule_conflict_propagates_and_saves_nothing() {
        // Given a room that already hosts another section at the same schedule
        Room room = newDefaultRoom();
        new Section("OtherSection", DEFAULT_SUBJECT, MTH830to10, room);
        when(subjectRepo.findById(DEFAULT_SUBJECT_ID)).thenReturn(Optional.of(DEFAULT_SUBJECT));
        when(roomRepo.findByIdForUpdate(DEFAULT_ROOM_NAME)).thenReturn(Optional.of(room));

        // When a section is created at an overlapping schedule in that room
        // Then the conflict propagates, so handleException() can show it
        // (ScheduleConflictException is package-private, so check it by name)
        EnlistmentException exception = assertThrows(EnlistmentException.class,
                () -> controller.createSection(DEFAULT_SECTION_ID, DEFAULT_SUBJECT_ID, MTH,
                        START, END, DEFAULT_ROOM_NAME, redirectAttrs));
        assertEquals("ScheduleConflictException", exception.getClass().getSimpleName());

        // And nothing is saved
        verify(sectionRepo, never()).save(any());
    }

    @Test
    void createSection_with_non_alphanumeric_section_id_throws_illegal_argument_and_saves_nothing() {
        // Given a subject and a room that exist
        when(subjectRepo.findById(DEFAULT_SUBJECT_ID)).thenReturn(Optional.of(DEFAULT_SUBJECT));
        when(roomRepo.findByIdForUpdate(DEFAULT_ROOM_NAME)).thenReturn(Optional.of(newDefaultRoom()));

        // When the section id is not alphanumeric
        // Then IllegalArgumentException is thrown; handleException() does not catch it, so the admin sees a 500 page
        assertThrows(IllegalArgumentException.class,
                () -> controller.createSection("bad id!", DEFAULT_SUBJECT_ID, MTH,
                        START, END, DEFAULT_ROOM_NAME, redirectAttrs));
        verify(sectionRepo, never()).save(any());
    }

    @Test
    void createSection_with_period_not_on_30_minute_boundary_throws_and_saves_nothing() {
        // Given a subject and a room that exist
        when(subjectRepo.findById(DEFAULT_SUBJECT_ID)).thenReturn(Optional.of(DEFAULT_SUBJECT));
        when(roomRepo.findByIdForUpdate(DEFAULT_ROOM_NAME)).thenReturn(Optional.of(newDefaultRoom()));

        // When the start time is not on :00 or :30
        // Then InvalidTimeException is thrown (package-private, so check it by name)
        EnlistmentException exception = assertThrows(EnlistmentException.class,
                () -> controller.createSection(DEFAULT_SECTION_ID, DEFAULT_SUBJECT_ID, MTH,
                        "08:45", END, DEFAULT_ROOM_NAME, redirectAttrs));
        assertEquals("InvalidTimeException", exception.getClass().getSimpleName());
        verify(sectionRepo, never()).save(any());
    }

    @Test
    void createSection_with_existing_section_id_throws_and_saves_nothing() {
        // Given a section id that already exists in the repository
        when(sectionRepo.existsById(DEFAULT_SECTION_ID)).thenReturn(true);

        // When creating a section with that existing id
        // Then DuplicateSectionException is thrown and nothing is saved
        assertThrows(DuplicateSectionException.class,
                () -> controller.createSection(DEFAULT_SECTION_ID, DEFAULT_SUBJECT_ID, MTH,
                        START, END, DEFAULT_ROOM_NAME, redirectAttrs));
        verify(sectionRepo, never()).save(any());
    }
}
