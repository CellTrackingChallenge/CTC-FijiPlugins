package de.mpicbg.ulman;

import de.mpicbg.ulman.workers.SEG;
import de.mpicbg.ulman.workers.TrackDataCache;
import de.mpicbg.ulman.workers.TrackDataCache.TemporalLevel;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Context;
import org.scijava.log.LogService;

import java.util.ArrayList;

public class testSEG {
    public static void minimalisticRunOfSEG()
    {
        try {
            final SEG seg = new SEG( new Context( LogService.class ).getService( LogService.class ) );
            seg.doLogReports = false;

            final double mySEGperformance = seg.calculate("somePath/to/folder/with/GT", "somePath/to/folder/with/results");
            System.out.println("SEG is "+mySEGperformance);
        }
        catch (RuntimeException e) {
            System.out.println("CTC SEG measure problem: "+e.getMessage());
        }
        catch (Exception e) {
            System.out.println("CTC SEG measure error: "+e.getMessage());
        }
    }


    public static void runSEGonImagePair()
    {
        try {
            //common logger for everyone in this story....
            final LogService log = new Context(LogService.class).getService(LogService.class);

            //read the image pair
            final TrackDataCache cache = new TrackDataCache(log);

            IterableInterval<UnsignedShortType> gt_img
               = cache.ReadImageG16("some/path");

            RandomAccessibleInterval<UnsignedShortType> res_img
               = cache.ReadImageG16("some/path");

            //use SEG...
            final SEG seg = new SEG(log);
            seg.doLogReports = true;

            final double overlapRatio = 0.5;
            final ArrayList<Integer> TP = new ArrayList<>(1000);
            final ArrayList<Integer> FP = new ArrayList<>(1000);
            final int[] FNcnt = new int[1];

            double segScore = seg.calculateDetections(gt_img,res_img,cache, overlapRatio, TP,FP,FNcnt);
            //NB: we're enjoying the fact that some 'cache' object is available around,
            //    otherwise the following line would work too (this time without the 'cache')
            //double segScore = seg.calculateDetections(gt_img,res_img, overlapRatio, TP,FP,FNcnt);

            //processing output
            System.out.println("SEG is "+segScore);

            System.out.print("TP res labels: ");
            for (Integer label : TP)
               System.out.print(label+",");

            System.out.print("\nFP res labels: ");
            for (Integer label : FP)
               System.out.print(label+",");

            System.out.println("\nnumber of FN (undetected) gt labels: "+FNcnt[0]);
        }
        catch (RuntimeException e) {
            System.out.println("CTC SEG measure problem: "+e.getMessage());
        }
        catch (Exception e) {
            System.out.println("CTC SEG measure error: "+e.getMessage());
        }
    }

    public static void runSEGonImagePair(final IterableInterval<UnsignedShortType> gt_img,
                                         final RandomAccessibleInterval<UnsignedShortType> res_img,
                                         final double overlapRatio)
    {
        try {
            //use SEG...
            final SEG seg = new SEG( new Context(LogService.class).getService(LogService.class) );

            final ArrayList<Integer> TP = new ArrayList<>(1000);
            final ArrayList<Integer> FP = new ArrayList<>(1000);
            final int[] FNcnt = new int[1];

            double segScore = seg.calculateDetections(gt_img,res_img, overlapRatio, TP,FP,FNcnt);

            //processing output
            System.out.println("SEG is "+segScore);

            System.out.print("TP res labels: ");
            for (Integer label : TP)
               System.out.print(label+",");

            System.out.print("\nFP res labels: ");
            for (Integer label : FP)
               System.out.print(label+",");

            System.out.println("\nnumber of FN (undetected) gt labels: "+FNcnt[0]);
        }
        catch (RuntimeException e) {
            System.out.println("CTC SEG measure problem: "+e.getMessage());
        }
        catch (Exception e) {
            System.out.println("CTC SEG measure error: "+e.getMessage());
        }
    }


    public static void main(final String... args)
    {
        minimalisticRunOfSEG();
        runSEGonImagePair();
    }
}
