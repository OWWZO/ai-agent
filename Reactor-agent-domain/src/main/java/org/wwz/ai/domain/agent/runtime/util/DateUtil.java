package org.wwz.ai.domain.agent.runtime.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    public static String CurrentDateInfo() {
        // 获取当前日期
        LocalDate currentDate = LocalDate.now();

        // 获取月份和日期
        int month = currentDate.getMonthValue();
        int day = currentDate.getDayOfMonth();

        // 获取星期几
        DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
        String dayOfWeekString = dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault());

        // 格式化输出
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年M月d日");
        String formattedDate = currentDate.format(formatter);

        return "今天是 " + formattedDate + " " + dayOfWeekString;
    }
}
