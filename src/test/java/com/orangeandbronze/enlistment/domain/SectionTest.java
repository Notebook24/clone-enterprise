package com.orangeandbronze.enlistment.domain;

import org.junit.jupiter.api.*;

import static com.orangeandbronze.enlistment.domain.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class SectionTest {

    @Test
    void newSection_same_room_diff_sked() {
        Room room = newDefaultRoom();
        Section defaultSec = new SectionBuilder().withSchedule(MTH830to10).withRoom(room).build();
        assertDoesNotThrow(() -> new SectionBuilder("B").withSchedule(TF10to1130).withRoom(room).build());
    }

    @Test
    void newSection_same_room_same_sked() {
        Room room = newDefaultRoom();
        Section defaultSec = new SectionBuilder().withSchedule(MTH830to10).withRoom(room).build();
        assertThrows(ScheduleConflictException.class, () -> new SectionBuilder("B").withSchedule(MTH830to10).withRoom(room).build());
    }

}
