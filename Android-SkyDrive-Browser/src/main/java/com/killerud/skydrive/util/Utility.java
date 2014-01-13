package com.killerud.skydrive.util;

import java.util.Random;

public class Utility
{
    public static int getRandomNuberFromRange(int from, int to)
    {
        Random r = new Random();
        r.setSeed(System.currentTimeMillis());
        return from + r.nextInt(to - from);
    }

    public static int getRandomNumber()
    {
        Random r = new Random();
        r.setSeed(System.currentTimeMillis());
        return r.nextInt();
    }

    public static int getRandomNumberFromRangeCustomSeed(int from, int to,
                                                         long seed)
    {
        Random r = new Random();
        r.setSeed(seed);
        return from + r.nextInt(to - from);
    }

    public static int getRandomNumberCustomSeed(long seed)
    {
        Random r = new Random();
        r.setSeed(seed);
        return r.nextInt();
    }

}
