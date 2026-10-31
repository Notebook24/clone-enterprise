package com.orangeandbronze.enlistment.controllers;

import com.orangeandbronze.enlistment.domain.*;
import com.orangeandbronze.enlistment.repositories.*;
import jakarta.persistence.EntityManager;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.*;
import org.springframework.ui.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.*;
import org.springframework.web.servlet.view.*;

import jakarta.transaction.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

@Controller
@RequestMapping("enlist")
@SessionAttributes("student")
class EnlistController {

    private static final int MIN_STUDENT_NUMBER = 1;
    private static final int MAX_STUDENT_NUMBER = 3;

    private final SectionRepository sectionRepo;
    private final StudentRepository studentRepo;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    EnlistController(SectionRepository sectionRepo, StudentRepository studentRepo, EntityManager entityManager, TransactionTemplate transactionTemplate) {
        // no null check for easier testing
        this.sectionRepo = sectionRepo;
        this.studentRepo = studentRepo;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    @ModelAttribute
    public void initStudent(Model model, Integer studentNumber) {
        Student student = (Student) model.getAttribute("student");
        if (studentNumber == null && student == null) {
            throw new LoginException("both studentNumber & student are null");
        }
        if (studentNumber != null && (studentNumber < MIN_STUDENT_NUMBER || studentNumber > MAX_STUDENT_NUMBER)) {
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

    @Retryable(retryFor =ConcurrencyFailureException.class, maxAttempts = 10,
        backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 500, random = true))
    @PostMapping
    public String enlistOrCancel(@RequestParam String sectionId,
                                 @RequestParam UserAction userAction, Model model) {
        Student student = (Student) model.getAttribute("student");
        Student updated = transactionTemplate.execute(status -> {
            Student managed = entityManager.merge(student);
            Section section = sectionRepo.findById(sectionId).orElseThrow(
                    () -> new NoSuchElementException("No section for sectionId " + sectionId));
            userAction.act(managed, section);
            studentRepo.save(managed);
            sectionRepo.save(section);
            return managed;
        });
        // Only touch the model after the transaction commits, so a retry starts
        // from the original student.
        model.addAttribute("student", updated);
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
