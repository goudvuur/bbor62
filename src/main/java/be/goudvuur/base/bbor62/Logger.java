package be.goudvuur.base.bbor62;

/**
 * Created by bram on Jul 29, 2025
 */
public class Logger
{
    //-----CONSTANTS-----

    //-----VARIABLES-----

    //-----CONSTRUCTORS-----

    //-----PUBLIC METHODS-----
    public static void log(Object o)
    {
        System.out.println(o);
    }
    public static void error(String msg)
    {
        error(msg, null);
    }
    public static void error(String msg, Throwable e)
    {
        System.out.println(msg);
        if (e != null) {
            e.printStackTrace();
        }
    }

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
}
