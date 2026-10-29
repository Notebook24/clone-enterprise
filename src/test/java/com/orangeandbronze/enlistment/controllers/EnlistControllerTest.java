package com.orangeandbronze.enlistment.controllers;

import com.orangeandbronze.enlistment.domain.*;
import com.orangeandbronze.enlistment.repositories.*;

import jakarta.persistence.EntityManager;
import org.hibernate.*;
import org.junit.jupiter.api.*;
import org.springframework.ui.Model;

import jakarta.persistence.*;

import java.util.*;

import static com.orangeandbronze.enlistment.domain.TestUtils.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jdk.jfr.internal.util.UserDataException;


class EnlistControllerTest {

    @Test
    void enlistOrCancel_enlist_student_in_section() {
        SectionRepository sectionRepository = mock(SectionRepository.class);
        Section section = newDefaultSection();
        when(sectionRepository.findById(DEFAULT_SECTION_ID)).thenReturn(Optional.of(section));
        StudentRepository studentRepository = mock(StudentRepository.class);
        EntityManager em = mock(EntityManager.class);
        EnlistController controller = new EnlistController(sectionRepository, studentRepository, em);
        Model model = mock(Model.class);
        Student student = mock(Student.class);
        when(model.getAttribute("student")).thenReturn(student);
        when(em.merge(student)).thenReturn(student);
        String returnVal = controller.enlistOrCancel(DEFAULT_SECTION_ID, UserAction.ENLIST, model);
        assertAll(
                //fetch the student object from the model
                () -> verify(model).getAttribute("student"),
                //reattach or refetch the student object to the JPA session
                () -> verify(em).merge(student),
                //fetch a section object from the database using the sectionId param
                () -> verify(sectionRepository).findById(DEFAULT_SECTION_ID),
                //pass the section object into the enlist method of the student object
                () -> verify(student).enlist(section),
                //save student to db
                () -> verify(studentRepository).save(student),
                //save section to db
                () -> verify(sectionRepository).save(section),
                //return a redirect back to the same "enlist" page
                () -> assertEquals(returnVal, "redirect:enlist")
        );
    }

}
