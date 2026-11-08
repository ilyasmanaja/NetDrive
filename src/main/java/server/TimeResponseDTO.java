package server;

import com.google.gson.annotations.SerializedName;

public class TimeResponseDTO {

    @SerializedName("year")
    public int year;

    @SerializedName("month")
    public int month;

    @SerializedName("day")
    public int day;

    @SerializedName("time")
    public String time;

    @SerializedName("dateTime")
    public String dateTime;

    @SerializedName("date")
    public String date;

    @SerializedName("timeZone")
    public String timeZone;

    @SerializedName("dayOfWeek")
    public String dayOfWeek;
}