package de.mpicbg.ulman;

import de.mpicbg.ulman.workers.TRA;
import org.scijava.Context;
import org.scijava.log.LogService;

public class testTRA_minimal {
    public static void runTRA()
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
        runTRA();
    }
}
