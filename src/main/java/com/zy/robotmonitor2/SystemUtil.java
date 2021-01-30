package com.zy.robotmonitor2;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class SystemUtil {

    public static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    public static Date parseTime(String dateTimeStr) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS);
        return sdf.parse(dateTimeStr);
    }

    public static String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS);
        return sdf.format(new Date());
    }

    public static void validateExitSystem(Date nowTime, String endTime) {
        try {
            Calendar date = Calendar.getInstance();
            date.setTime(nowTime);

            Calendar end = Calendar.getInstance();
            end.setTime(parseTime(endTime));

            boolean before = date.before(end);

            //到点了就退出JVM
            if (!before){
                System.out.println("已经到达结束时间， JVM即将在5s后退出");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.exit(0);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static boolean inTime(Date nowTime, String beginTime,
                                         String endTime) {
        boolean result = false;
        try {
            Calendar date = Calendar.getInstance();
            date.setTime(nowTime);

            Calendar begin = Calendar.getInstance();
            begin.setTime(parseTime(beginTime));

            Calendar end = Calendar.getInstance();
            end.setTime(parseTime(endTime));

            result = date.after(begin) && date.before(end);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return result;

    }
}
