package com.orangeandbronze.enlistment.controllers;

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
        when(roomRepo.findById(DEFAULT_ROOM_NAME)).thenReturn(Optional.of(room));

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
}