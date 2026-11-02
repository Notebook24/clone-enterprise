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

}
