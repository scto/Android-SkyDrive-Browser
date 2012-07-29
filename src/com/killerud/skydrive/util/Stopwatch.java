package com.killerud.skydrive.util;

/**
 * Created with IntelliJ IDEA.
 * User: William
 * Date: 29.07.12
 * Time: 18:58
 * To change this template use File | Settings | File Templates.
 */
public class Stopwatch
{
    private final long start;

    public Stopwatch()
    {
        start = System.currentTimeMillis();
    }

    public long elapsedTimeInSeconds()
    {
        return (System.currentTimeMillis() - start)/1000;
    }
}
