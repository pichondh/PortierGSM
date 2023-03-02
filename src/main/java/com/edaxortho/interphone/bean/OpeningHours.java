package com.edaxortho.interphone.bean;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class OpeningHours {

    private LocalTime mondayOpen;
    private LocalTime mondayClose;
    private LocalTime tuesdayOpen;
    private LocalTime tuesdayClose;
    private LocalTime wednesdayOpen;
    private LocalTime wednesdayClose;
    private LocalTime thursdayOpen;
    private LocalTime thursdayClose;
    private LocalTime fridayOpen;

    public LocalTime getMondayOpen() {
        return mondayOpen;
    }

    public void setMondayOpen(LocalTime mondayOpen) {
        this.mondayOpen = mondayOpen;
    }

    public LocalTime getMondayClose() {
        return mondayClose;
    }

    public void setMondayClose(LocalTime mondayClose) {
        this.mondayClose = mondayClose;
    }

    public LocalTime getTuesdayOpen() {
        return tuesdayOpen;
    }

    public void setTuesdayOpen(LocalTime tuesdayOpen) {
        this.tuesdayOpen = tuesdayOpen;
    }

    public LocalTime getTuesdayClose() {
        return tuesdayClose;
    }

    public void setTuesdayClose(LocalTime tuesdayClose) {
        this.tuesdayClose = tuesdayClose;
    }

    public LocalTime getWednesdayOpen() {
        return wednesdayOpen;
    }

    public void setWednesdayOpen(LocalTime wednesdayOpen) {
        this.wednesdayOpen = wednesdayOpen;
    }

    public LocalTime getWednesdayClose() {
        return wednesdayClose;
    }

    public void setWednesdayClose(LocalTime wednesdayClose) {
        this.wednesdayClose = wednesdayClose;
    }

    public LocalTime getThursdayOpen() {
        return thursdayOpen;
    }

    public void setThursdayOpen(LocalTime thursdayOpen) {
        this.thursdayOpen = thursdayOpen;
    }

    public LocalTime getThursdayClose() {
        return thursdayClose;
    }

    public void setThursdayClose(LocalTime thursdayClose) {
        this.thursdayClose = thursdayClose;
    }

    public LocalTime getFridayOpen() {
        return fridayOpen;
    }

    public void setFridayOpen(LocalTime fridayOpen) {
        this.fridayOpen = fridayOpen;
    }

    public LocalTime getFridayClose() {
        return fridayClose;
    }

    public void setFridayClose(LocalTime fridayClose) {
        this.fridayClose = fridayClose;
    }

    public LocalTime getSaturdayOpen() {
        return saturdayOpen;
    }

    public void setSaturdayOpen(LocalTime saturdayOpen) {
        this.saturdayOpen = saturdayOpen;
    }

    public LocalTime getSaturdayClose() {
        return saturdayClose;
    }

    public void setSaturdayClose(LocalTime saturdayClose) {
        this.saturdayClose = saturdayClose;
    }

    public LocalTime getSundayOpen() {
        return sundayOpen;
    }

    public void setSundayOpen(LocalTime sundayOpen) {
        this.sundayOpen = sundayOpen;
    }

    public LocalTime getSundayClose() {
        return sundayClose;
    }

    public void setSundayClose(LocalTime sundayClose) {
        this.sundayClose = sundayClose;
    }

    private LocalTime fridayClose;
    private LocalTime saturdayOpen;
    private LocalTime saturdayClose;
    private LocalTime sundayOpen;
    private LocalTime sundayClose;

    public OpeningHours() {
    }

    public LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time);
        } catch (Exception e) {
            return LocalTime.MIDNIGHT;
        }
    }

    @Override
    public String toString() {
        return "OpeningHours{" +
                "mondayOpen=" + mondayOpen +
                ", mondayClose=" + mondayClose +
                ", tuesdayOpen=" + tuesdayOpen +
                ", tuesdayClose=" + tuesdayClose +
                ", wednesdayOpen=" + wednesdayOpen +
                ", wednesdayClose=" + wednesdayClose +
                ", thursdayOpen=" + thursdayOpen +
                ", thursdayClose=" + thursdayClose +
                ", fridayOpen=" + fridayOpen +
                ", fridayClose=" + fridayClose +
                ", saturdayOpen=" + saturdayOpen +
                ", saturdayClose=" + saturdayClose +
                ", sundayOpen=" + sundayOpen +
                ", sundayClose=" + sundayClose +
                '}';
    }

    public LocalTime getOpeningTime(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return mondayOpen;
            case TUESDAY:
                return tuesdayOpen;
            case WEDNESDAY:
                return wednesdayOpen;
            case THURSDAY:
                return thursdayOpen;
            case FRIDAY:
                return fridayOpen;
            case SATURDAY:
                return saturdayOpen;
            case SUNDAY:
                return sundayOpen;
            default:
                return null;
        }
    }

    public LocalTime getClosingTime(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return mondayClose;
            case TUESDAY:
                return tuesdayClose;
            case WEDNESDAY:
                return wednesdayClose;
            case THURSDAY:
                return thursdayClose;
            case FRIDAY:
                return fridayClose;
            case SATURDAY:
                return saturdayClose;
            case SUNDAY:
                return sundayClose;
            default:
                return null;
        }
    }
}
