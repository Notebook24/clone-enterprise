package com.orangeandbronze.enlistment.domain;

import static com.orangeandbronze.enlistment.domain.TestUtils.*;

public class SectionBuilder {
    // Defaults
    private String sectionId = DEFAULT_SECTION_ID;
    private Subject subject = DEFAULT_SUBJECT;
    private Schedule schedule = MTH830to10;
    private Room room = ROOMX;

    public SectionBuilder withSectionId(String sectionId) {
        this.sectionId = sectionId;
        return this;
    }

    public SectionBuilder withSubject(Subject subject) {
        this.subject = subject;
        return this;
    }

    public SectionBuilder withSchedule(Schedule schedule) {
        this.schedule = schedule;
        return this;
    }

    public SectionBuilder withRoom(Room room) {
        this.room = room;
        return this;
    }

    public Section build() {
        return new Section(sectionId, subject, schedule, room);
    }
}