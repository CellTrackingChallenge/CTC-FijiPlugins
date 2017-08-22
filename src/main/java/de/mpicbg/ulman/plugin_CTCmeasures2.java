/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.log.LogService;
import net.imagej.ImageJ;

import de.mpicbg.ulman.workers.TrackDataCache;
import de.mpicbg.ulman.workers.TRA;
import de.mpicbg.ulman.workers.SEG;
import de.mpicbg.ulman.workers.CT;
import de.mpicbg.ulman.workers.TF;
import de.mpicbg.ulman.workers.BCi;
import de.mpicbg.ulman.workers.CCA;

@Plugin(type = Command.class, menuPath = "Plugins>CTC>Dataset quality measures",
        name = "CTC_ALL2", headless = true,
		  description = "Calculates all tracking performance measures from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/Submission_of_Results.html")
public class plugin_CTCmeasures2 implements Command
{
	//------------- GUI stuff -------------
	//
	@Parameter
	private LogService log;

	@Parameter(label = "Path to images folder: ",
		columns = 40,
		description = "Path should contain cell image files directly: t???.tif")
	private String imgPath;

	@Parameter(label = "Path to annotations folder: ",
		columns = 40,
		description = "Path should contain folders BG and TRA and annotation files: BG/mask???.tif, TRA/man_track???.tif and man_track.txt")
	private String annPath;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String pathFooterA
		= "Note that folders has to comply with certain data format, please see";
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String pathFooterB
		= "http://www.celltrackingchallenge.net/Submission_of_Results.html";


	@Parameter(visibility = ItemVisibility.MESSAGE, label = "Select measures to calculate:")
	private final String measuresHeader = "";

	@Parameter(label = "foo",
		description = "Evaluates the foo.")
	private boolean calcFOO = true;


	//citation footer...
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String citationFooter
		= "Please, cite us.... TBA";


	//hidden output values
	@Parameter(type = ItemIO.OUTPUT)
	String IMGdir;
	@Parameter(type = ItemIO.OUTPUT)
	String ANNdir;
	@Parameter(type = ItemIO.OUTPUT)
	String sep = "--------------------";

	@Parameter(type = ItemIO.OUTPUT)
	double FOO = -1;


	//the GUI path entry function:
	@Override
	public void run()
	{
		try {
			//WE GONNA USE THE SAME CACHE-BASED TEMPLATE HERE TOO

			//reference on a shared object that does
			//pre-fetching of data and some common pre-calculation
			/*
			TrackDataCache cache = null;

			//start up the independent workers
			final TRA tra = new TRA(log);
			final SEG seg = new SEG(log);
			final CT  ct  = new CT(log);
			final TF  tf  = new TF(log);
			final BCi bci = new BCi(log);
			final CCA cca = new CCA(log);

			//set this one before we start any calculation
			bci.setI(iForBCi);

			GTdir  = gtPath;
			RESdir = resPath;

			//do the calculation and retrieve updated cache afterwards
			if (calcTRA)
			{
				TRA = tra.calculate(gtPath,resPath,cache);
				cache = tra.getCache();
			}

			//SEG is whole different from the tracking-oriented rest,
			//thus, it cannot really utilize the shared/cached data
			if (calcSEG)
				SEG = seg.calculate(gtPath,resPath);

			if (calcCT )
			{
				CT = ct.calculate(gtPath,resPath,cache);
				cache = ct.getCache();
			}

			if (calcTF )
			{
				TF = tf.calculate(gtPath,resPath,cache);
				cache = tf.getCache();
			}

			if (calcBCi)
			{
				BCi = bci.calculate(gtPath,resPath,cache);
				cache = bci.getCache();
			}

			if (calcCCA)
			{
				CCA = cca.calculate(gtPath,resPath,cache);
				cache = cca.getCache();
			}
			*/

			//do not report anything explicitly (unless special format for parsing is
			//desired) as ItemIO.OUTPUT will make it output automatically
		}
		catch (RuntimeException e) {
			log.error("CTC measures problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("CTC measures error: "+e.getMessage());
		}
	}


	//------------- command line stuff -------------
	//
	//the CLI path entry function:
	public static void main(final String... args)
	{
		//check the input parameters

		//parse and store the arguments, if necessary
		//....

		//start up our own ImageJ without GUI
		final ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();

		//run this class as if from GUI
		//...

		//and close the IJ instance...
		//ij.appEvent().quit();
	}
}
