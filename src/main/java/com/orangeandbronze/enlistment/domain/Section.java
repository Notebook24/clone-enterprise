package com.orangeandbronze.enlistment.domain;

import jakarta.persistence.*;
import jakarta.persistence.Entity;
import org.springframework.data.domain.Persistable;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.*;
import static org.apache.commons.lang3.Validate.*;

@Entity
public class Section implements Persistable<String> {
    @Id
    private final String sectionId;
    @ManyToOne
    private final Subject subject;
    @Embedded
    private final Schedule schedule;
    @ManyToOne
    private final Room room;

    private int numberOfStudents = 0;

    @Transient
    private boolean isNew = true;

    public Section(String sectionId, Subject subject, Schedule schedule, Room room) {
        notBlank(sectionId,
                "sectionId can't be null, empty or whitespace ");
        notNull(subject, "subject can't be null");
        isTrue(isAlphanumeric(sectionId),
                "sectionId must be alphanumeric, was: "
                        + sectionId);
        notNull(room, "room can't be null");
        this.sectionId = sectionId;
        this.subject = subject;
        this.schedule = schedule;
        room.addSection(this);
        this.room = room;
    }

    Section(String sectionId, Subject subject, Schedule schedule, Room room, int numberOfStudents) {
        this(sectionId, subject, schedule, room);
        isTrue(numberOfStudents >= 0,
                "numberOfStudents must be non-negative, was: " + numberOfStudents);
        this.numberOfStudents = numberOfStudents;
    }

    void checkSameSubject(Section other) {
        if (this.subject.equals(other.subject)) {
            throw new SameSubjectException("This section " + this + " & other section " + other +
                    " have same subject of " + subject);
        }
    }

    void checkForScheduleConflict(Section other) {
        this.schedule.checkOverlap(other.schedule);
    }

    int getNumberOfStudents() {
        return numberOfStudents;
    }

    void incrementNumberOfStudents() {
        room.checkIfAtOrOverCapacity(numberOfStudents);
        numberOfStudents++;
    }

    void decrementNumberOfStudents() {
        numberOfStudents--;
    }

    void checkPrereqs(Collection<Subject> subjectsTaken) {
        notNull(subjectsTaken, "subjectsTaken can't be null");
        Collection<Subject> copy = new HashSet<>(subjectsTaken); // sets are quicker to search through
        subject.checkPrereqs(copy);
    }

    public String getSectionId() {
        return sectionId;
    }

    @Override
    public String getId() {
        return sectionId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public Subject getSubject() {
        return subject;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public Room getRoom() {
        return room;
    }

    @Override
    public String toString() {
        return sectionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Section section = (Section) o;

        return Objects.equals(sectionId, section.sectionId);
    }

    @Override
    public int hashCode() {
        return sectionId != null ? sectionId.hashCode() : 0;
    }

    // For JPA only. Do not call!
    private Section() {
        sectionId = null;
        subject = null;
        schedule = null;
        room = null;
    }
}
