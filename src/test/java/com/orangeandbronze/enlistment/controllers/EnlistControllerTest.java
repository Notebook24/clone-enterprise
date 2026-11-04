package com.orangeandbronze.enlistment.controllers;

import com.orangeandbronze.enlistment.domain.*;
import com.orangeandbronze.enlistment.repositories.*;

import jakarta.persistence.EntityManager;
import org.hibernate.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.ui.Model;

import jakarta.persistence.*;

import java.util.*;

import static com.orangeandbronze.enlistment.domain.TestUtils.*;

import static com.orangeandbronze.enlistment.controllers.UserAction.CANCEL;
import static com.orangeandbronze.enlistment.controllers.UserAction.ENLIST;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnlistControllerTest {

    private final SectionRepository sectionRepository = mock(SectionRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final EnlistController controller = new EnlistController(sectionRepository, studentRepository, em, transactionTemplate);

    @BeforeEach
    void runTransactionCallbacksDirectly() {
        // No real transaction in a unit test: just run the callback.
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(mock(TransactionStatus.class)));
    }

    @Test
    void enlistOrCancel_enlist_student_in_section() {
        //Given a student in the model and a section in the repository
        Student student = newDefaultStudent();
        Section section = newDefaultSection();
        Model model = mock(Model.class);
        when(model.getAttribute("student")).thenReturn(student);
        when(em.merge(student)).thenReturn(student);
        when(sectionRepository.findByIdForUpdate(DEFAULT_SECTION_ID)).thenReturn(Optional.of(section));

        //When the student enlists in the section
        String returnVal = controller.enlistOrCancel(DEFAULT_SECTION_ID, ENLIST, model);
        
        //Then the student and section are saved, the student is enlisted,
            //and the user is redirected back to the enlist page
        ArgumentCaptor<Student> savedStudent = ArgumentCaptor.forClass(Student.class);
        ArgumentCaptor<Section> savedSection = ArgumentCaptor.forClass(Section.class);
        verify(studentRepository).save(savedStudent.capture());
        verify(sectionRepository).save(savedSection.capture());
        assertAll(
                //fetch the student object from the model
                () -> verify(model).getAttribute("student"),
                //reattach or refetch the student object to the JPA session
                () -> verify(em).merge(student),
                //fetch a section object from the database using the sectionId param
                () -> verify(sectionRepository).findByIdForUpdate(DEFAULT_SECTION_ID),
                //pass the section object into the enlist method of the student object
                () -> assertSame(student, savedStudent.getValue()),
                () -> assertSame(section, savedSection.getValue()),
                () -> assertTrue(savedStudent.getValue().getSections().contains(section)),
                () -> verify(model).addAttribute("student", student),
                //save student to db
                () -> verify(studentRepository).save(student),
                //save section to db
                () -> verify(sectionRepository).save(section),
                //return a redirect back to the same "enlist" page
                () -> assertEquals("redirect:enlist", returnVal)
        );
    }

    @Test
    void enlistOrCancel_cancel_student_in_section() {
        //Given a student already enlisted in a section
        Section section = new SectionBuilder().noStudents(1).build();
        Student student = newStudent(DEFAULT_STUDENT_NUMBER, List.of(section));
        Model model = mock(Model.class);
        when(model.getAttribute("student")).thenReturn(student);
        when(em.merge(student)).thenReturn(student);
        when(sectionRepository.findByIdForUpdate(DEFAULT_SECTION_ID)).thenReturn(Optional.of(section));

        //When the student cancels the section
        String returnVal = controller.enlistOrCancel(DEFAULT_SECTION_ID, CANCEL, model);

        //Then the section is removed from the student, its count is decremented,
            //and both the student and section are saved
        ArgumentCaptor<Student> savedStudent = ArgumentCaptor.forClass(Student.class);
        ArgumentCaptor<Section> savedSection = ArgumentCaptor.forClass(Section.class);
        verify(studentRepository).save(savedStudent.capture());
        verify(sectionRepository).save(savedSection.capture());
        assertAll(
                () -> verify(model).getAttribute("student"),
                () -> verify(em).merge(student),
                () -> verify(sectionRepository).findByIdForUpdate(DEFAULT_SECTION_ID),
                () -> assertSame(student, savedStudent.getValue()),
                () -> assertSame(section, savedSection.getValue()),
                () -> assertFalse(savedStudent.getValue().getSections().contains(section)),
                () -> verify(model).addAttribute("student", student),
                () -> assertEquals("redirect:enlist", returnVal)
        );
    }

    @Test
    void enlistOrCancel_cancel_non_enlisted_section_does_not_change_count() {
        //Given a student who is not enlisted in a section
        Section section = new SectionBuilder().noStudents(1).build();
        Student student = newDefaultStudent();
        Model model = mock(Model.class);
        when(model.getAttribute("student")).thenReturn(student);
        when(em.merge(student)).thenReturn(student);
        when(sectionRepository.findByIdForUpdate(DEFAULT_SECTION_ID)).thenReturn(Optional.of(section));

        //When the student cancels that section
        String returnVal = controller.enlistOrCancel(DEFAULT_SECTION_ID, CANCEL, model);

        //Then nothing changes for the student or the section
        ArgumentCaptor<Student> savedStudent = ArgumentCaptor.forClass(Student.class);
        ArgumentCaptor<Section> savedSection = ArgumentCaptor.forClass(Section.class);
        verify(studentRepository).save(savedStudent.capture());
        verify(sectionRepository).save(savedSection.capture());
        assertAll(
                () -> assertTrue(savedStudent.getValue().getSections().isEmpty()),
                () -> verify(model).addAttribute("student", student),
                () -> assertEquals("redirect:enlist", returnVal)
        );
    }

}
