package com.edaxortho.interphone.util;

import com.edaxortho.interphone.bean.OpeningHours;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class OpeningHoursUtil {

    public boolean isOpen(OpeningHours openingHours, LocalDateTime dateTime) {
        LocalTime openingTime = openingHours.getOpeningTime(dateTime.getDayOfWeek());
        LocalTime closingTime = openingHours.getClosingTime(dateTime.getDayOfWeek());
        if (openingTime == null || closingTime == null || openingTime.equals(closingTime)) {
            return false;
        }
        LocalTime time = dateTime.toLocalTime();
        return !time.isBefore(openingTime) && !time.isAfter(closingTime);
    }
}
