package com.orangeandbronze.enlistment.domain;

import org.junit.jupiter.api.Test;

import static com.orangeandbronze.enlistment.domain.Days.MTH;
import static com.orangeandbronze.enlistment.domain.TestUtils.TIME_0830;
import static com.orangeandbronze.enlistment.domain.TestUtils.TIME_1000;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleTest {

    @Test
    void schedules_with_same_days_and_equal_periods_are_equal() {
        // Given two schedules built separately from the same days and times
        Schedule first = new Schedule(MTH, new Period(TIME_0830, TIME_1000));
        Schedule second = new Schedule(MTH, new Period(TIME_0830, TIME_1000));
        // Then they are equal and share a hash code
        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.hashCode(), second.hashCode())
        );
    }
}
