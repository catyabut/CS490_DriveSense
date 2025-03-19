package com.example.cs490_drivesense;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

public class SessionTimer {
    private ZonedDateTime start; // Start time var
    private ZonedDateTime stop;  // Stop time var


    // Get Current timestamp in phones time zone
    public ZonedDateTime getCurrentTime()
    {
        return ZonedDateTime.now();
    }

    // Start timer
    public void startTimer()
    {
        this.start = ZonedDateTime.now();
    }

    // Stop timer
    public void stopTimer()
    {
        this.stop = ZonedDateTime.now();
    }

    // Get elapsed time from start and stop
    public long getElapsedTime(ZonedDateTime start, ZonedDateTime stop)
    {
        Duration duration = Duration.between(start.toInstant(), stop.toInstant());
        return duration.getSeconds();
    }

    public ZonedDateTime getStart()
    {
        return this.start;
    }

    public ZonedDateTime getStop()
    {
        return this.stop;
    }

    public String getTimeStr(ZonedDateTime time)
    {
        return time.toString();
    }
}
