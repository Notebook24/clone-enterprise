package com.orangeandbronze.enlistment.controllers;

import com.orangeandbronze.enlistment.domain.*;
import com.orangeandbronze.enlistment.repositories.*;
import org.springframework.stereotype.*;
import org.springframework.ui.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.*;
import org.springframework.web.servlet.view.*;

import jakarta.transaction.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import jakarta.persistence.EntityManager;

@Transactional
@Controller
@RequestMapping("enlist")
@SessionAttributes("student")
class EnlistController {

    private final SectionRepository sectionRepo;
    private final StudentRepository studentRepo;
//    @PersistenceContext
    private final EntityManager entityManager;

    EnlistController(SectionRepository sectionRepo, StudentRepository studentRepo, EntityManager entityManager) {
        // no null check for easier testing
        this.sectionRepo = sectionRepo;
        this.studentRepo = studentRepo;
        this.entityManager = entityManager;
    }

    @ModelAttribute
    public void initStudent(Model model, Integer studentNumber) {
        Student student = (Student) model.getAttribute("student");
        if (studentNumber == null && student == null) {
            throw new LoginException("both studentNumber & student are null");
        }
        if (studentNumber != null && (studentNumber < 1 || studentNumber > 3)) {
            throw new LoginException("studentNumber out of range, was: " + studentNumber);
        }
        if (studentNumber != null) {
            student = studentRepo.findById(studentNumber).orElseThrow(() -> new NoSuchElementException("No student for studentNumber " + studentNumber));
            model.addAttribute(student);
        }
        model.addAttribute("isRetry", false);

    }

    @ExceptionHandler(LoginException.class)
    public RedirectView home() {
        return new RedirectView("login.html");
    }


    @GetMapping
    public String showSections(Model model, @ModelAttribute Student student) {
        var enlistedSections = student.getSections();
        model.addAttribute("enlistedSections", enlistedSections);
        model.addAttribute("availableSections", sectionRepo.findAll().stream()
                .filter(sec -> !enlistedSections.contains(sec)).collect(Collectors.toList()));
        return "enlist";
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 10)
    @PostMapping
    public String enlistOrCancel(@RequestParam String sectionId,
                                 @RequestParam UserAction userAction, Model model) {
        Student student = (Student) model.getAttribute("student");
        student = entityManager.merge(student);
        model.addAttribute("student", student);
        Section section = sectionRepo.findById(sectionId).orElseThrow();
        student.enlist(section);
        studentRepo.save(student);
        sectionRepo.save(section);
        return "redirect:enlist";
    }


    @ExceptionHandler(EnlistmentException.class)
    public String handleException(RedirectAttributes redirectAttrs, EnlistmentException e) {
        redirectAttrs.addFlashAttribute("enlistmentExceptionMessage", e.getMessage());
        return "redirect:enlist";
    }

}

enum UserAction {
    ENLIST(Student::enlist),
    CANCEL(Student::cancel);

    private final BiConsumer<Student, Section> action;

    UserAction(BiConsumer<Student, Section> action) {
        this.action = action;
    }

    void act(Student student, Section section) {
        action.accept(student, section);
    }

}
