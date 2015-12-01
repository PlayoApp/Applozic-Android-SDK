package com.applozic.mobicommons.commons.core.utils;

import android.os.SystemClock;

import net.mobitexter.mobiframework.commons.core.utils.SntpClient;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by devashish on 28/11/14.
 */
public class DateUtils {

    private static final String JUST_NOW = " Just now";
    private static final String MINUTES = " mins";
    private static final String HOURS = " hrs";
    private static final String H = "h";
    private static final String AGO = " ago";
    private static final String YESTERDAY = "Yesterday";

    public static boolean isSameDay(Long timestamp) {
        Calendar calendarForCurrent = Calendar.getInstance();
        Calendar calendarForScheduled = Calendar.getInstance();
        Date currentDate = new Date();
        Date date = new Date(timestamp);
        calendarForCurrent.setTime(currentDate);
        calendarForScheduled.setTime(date);
        return calendarForCurrent.get(Calendar.YEAR) == calendarForScheduled.get(Calendar.YEAR) &&
                calendarForCurrent.get(Calendar.DAY_OF_YEAR) == calendarForScheduled.get(Calendar.DAY_OF_YEAR);
    }

    public static String getFormattedDate(Long timestamp) {
       // boolean sameDay = isSameDay(timestamp);
        Date date = new Date(timestamp);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("hh:mm aa");
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("dd MMM");
        return  simpleDateFormat.format(date);
    }

    public static long getTimeDiffFromUtc() {
        SntpClient sntpClient = new SntpClient();
        long diff = 0;
        if (sntpClient.requestTime("0.africa.pool.ntp.org", 30000)) {
            long utcTime = sntpClient.getNtpTime() + SystemClock.elapsedRealtime() - sntpClient.getNtpTimeReference();
            diff = utcTime - System.currentTimeMillis();
        }
        return diff;
    }

    public static String getFormattedDateAndTime(Long timestamp) {
        boolean sameDay = isSameDay(timestamp);
        Date date = new Date(timestamp);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("hh:mm aa");
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("dd MMM");
        Date newDate = new Date();

        try {
            if (sameDay) {
                long currentTime = newDate.getTime() - date.getTime();
                long diffMinutes = TimeUnit.MILLISECONDS.toMinutes(currentTime);
                long diffHours = TimeUnit.MILLISECONDS.toHours(currentTime);
                if (diffMinutes <= 1 && diffHours == 0) {
                    return JUST_NOW;
                }
                if (diffMinutes <= 59 && diffHours == 0) {
                    return String.valueOf(diffMinutes) + MINUTES;
                }
                if (diffHours <= 2) {
                    return String.valueOf(diffHours) + H;
                }
                return simpleDateFormat.format(date);
            }
            return fullDateFormat.format(date);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getDateAndTimeForLastSeen(Long timestamp) {
        boolean sameDay = isSameDay(timestamp);
        Date date = new Date(timestamp);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, MMM dd,yyyy");

        try {
            if (sameDay) {
                Date newDate = new Date();
                long currentTime = newDate.getTime() - date.getTime();
                long diffMinutes = TimeUnit.MILLISECONDS.toMinutes(currentTime);
                long diffHours = TimeUnit.MILLISECONDS.toHours(currentTime);
                if (diffMinutes <= 1 && diffHours == 0) {
                    return JUST_NOW;
                }
                if (diffMinutes <= 59 && diffHours == 0) {
                    return String.valueOf(diffMinutes) + MINUTES + AGO;
                }
                if (diffHours < 24) {
                    return String.valueOf(diffHours) + HOURS + AGO;
                }
            }
            if (isYesterday(timestamp)) {
                return YESTERDAY;
            }
            return simpleDateFormat.format(date) + AGO;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;

    }

    public static boolean isYesterday(Long timestamp) {
        Calendar c1 = Calendar.getInstance();
        c1.add(Calendar.DAY_OF_YEAR, -1);
        Date date = new Date(timestamp);
        Calendar c2 = Calendar.getInstance();
        c2.setTime(date);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

}
