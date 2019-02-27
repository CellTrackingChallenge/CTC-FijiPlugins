package de.mpicbg.ulman;

import de.mpicbg.ulman.workers.TRA;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;
import org.scijava.thread.ThreadService;

import org.scijava.log.Logger;
import org.scijava.ui.swing.console.LoggingPanel;

import javax.swing.*;

public class testTRA {
    public static void consoleLogging()
    {
        // If you are a standalong Java console app
        final Context context = new Context( LogService.class );
        final Logger log = context.getService( LogService.class ).subLogger("TRA calculator");

        doYourLogging(log);
    }

    public static void guiLogging()
    {
        // If you are a standalong Java GUI app
        final Context context = new Context( LogService.class, ThreadService.class, PrefService.class);
        final Logger log = context.getService( LogService.class ).subLogger("TRA calculator");

        // GUI log panel
        final LoggingPanel logPanel = new LoggingPanel(context);
        log.addLogListener(logPanel);

        // this is where the panel will be displayed: empty JFrame
        final JFrame frame = new JFrame("TRA calculator's log");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(logPanel);
        frame.pack();
        frame.setVisible(true);

        doYourLogging(log);

        frame.dispose();
    }

    public static void doYourLogging(final Logger log)
    {
        System.out.println("logging started");
        log.info( "This is an info message" );
        log.error( "This is an error message" );

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("logging stopped");
    }


    public static void minimalisticRunOfTRA()
    {
        try {
            final TRA tra = new TRA( new Context( LogService.class ).getService( LogService.class ) );
            tra.doConsistencyCheck = true;
            tra.doLogReports = false;

            final double myTRAperformance = tra.calculate("somePath/to/folder/with/GT", "somePath/to/folder/with/results");
            System.out.println("TRA is "+myTRAperformance);
        }
        catch (RuntimeException e) {
            System.out.println("CTC TRA measure problem: "+e.getMessage());
        }
        catch (Exception e) {
            System.out.println("CTC TRA measure error: "+e.getMessage());
        }
    }


    public static void main(final String... args)
    {
        consoleLogging();
        guiLogging();

        minimalisticRunOfTRA();
    }
}
