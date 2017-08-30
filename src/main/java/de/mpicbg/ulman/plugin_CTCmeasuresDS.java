/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Vladimír Ulman
 */
package de.mpicbg.ulman;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.log.LogService;
import net.imagej.ImageJ;

import de.mpicbg.ulman.workers.ImgQualityDataCache;
import de.mpicbg.ulman.workers.SNR;
import de.mpicbg.ulman.workers.CR;
import de.mpicbg.ulman.workers.HETI;
import de.mpicbg.ulman.workers.HETB;
import de.mpicbg.ulman.workers.RES;
import de.mpicbg.ulman.workers.SHA;
import de.mpicbg.ulman.workers.DEN;
import de.mpicbg.ulman.workers.CHA;
import de.mpicbg.ulman.workers.OVE;
import de.mpicbg.ulman.workers.MIT;
/*
import de.mpicbg.ulman.workers.SYN;
import de.mpicbg.ulman.workers.ENTLEAV;
*/

@Plugin(type = Command.class, menuPath = "Plugins>Cell Tracking Challenge>Dataset measures",
        name = "CTC_DS", headless = true,
		  description = "Calculates dataset quality measures from the CTC paper.\n"
				+"The plugin assumes certain data format, please see\n"
				+"http://www.celltrackingchallenge.net/Submission_of_Results.html")
public class plugin_CTCmeasuresDS implements Command
{
	//------------- GUI stuff -------------
	//
	@Parameter
	private LogService log;

	@Parameter(label = "Path to images folder:",
		columns = 40,
		description = "Path should contain cell image files directly: t???.tif")
	private String imgPath;

	@Parameter(label = "Resolution (um/px) of the images, x-axis:",
		min = "0.0001", stepSize = "0.1",
		description = "Size of single pixel/voxel along the x-axis in micrometers.")
	double xRes = 1.0;

	@Parameter(label = "y-axis:",
		min = "0.0001", stepSize = "0.1",
		description = "Size of single pixel/voxel along the y-axis in micrometers.")
	double yRes = 1.0;

	@Parameter(label = "z-axis:",
		min = "0.0001", stepSize = "0.1",
		description = "Size of single pixel/voxel along the z-axis in micrometers.")
	double zRes = 1.0;

	@Parameter(label = "Path to annotations folder:",
		columns = 40,
		description = "Path should contain folders BG and TRA and annotation files: "
			+ "BG/mask???.tif, TRA/man_track???.tif and man_track.txt. "
			+ "The TRA/man_track???.tif must provide realistic masks of cells (not just blobs representing centres etc.).")
	private String annPath;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
	private final String pathFooterA
		= "Note that folders has to comply with certain data format, please see";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
	private final String pathFooterB
		= "http://www.celltrackingchallenge.net/Submission_of_Results.html";


	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false,
		label = "Select measures to calculate:")
	private final String measuresHeader = "";

	@Parameter(label = "SNR",
		description = "Evaluates the average signal to noise ratio over all annotated cells.")
	private boolean calcSNR = true;

	@Parameter(label = "CR",
		description = "Evaluates the foo.")
	private boolean calcCR = true;

	@Parameter(label = "Heti",
		description = "Evaluates the foo.")
	private boolean calcHeti = true;

	@Parameter(label = "Hetb",
		description = "Evaluates the foo.")
	private boolean calcHetb = true;

	@Parameter(label = "Res",
		description = "Evaluates the foo.")
	private boolean calcRes = true;

	@Parameter(label = "Sha",
		description = "Evaluates the foo.")
	private boolean calcSha = true;

	@Parameter(label = "Den",
		description = "Evaluates the foo.")
	private boolean calcDen = true;

	@Parameter(label = "Cha",
		description = "Evaluates the foo.")
	private boolean calcCha = true;

	@Parameter(label = "Ove",
		description = "Evaluates the foo.")
	private boolean calcOve = true;

	@Parameter(label = "Mit",
		description = "Evaluates the foo.")
	private boolean calcMit = true;

	/*
	@Parameter(label = "Syn",
		description = "Evaluates the foo.")
	private boolean calcSyn = true;

	@Parameter(label = "EntLeav",
		description = "Evaluates the foo.")
	private boolean calcEntLeav = true;
	*/


	//citation footer...
	/*
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
	private final String citationFooter
		= "Please, cite us.... TBA";
	*/


	//hidden output values
	@Parameter(type = ItemIO.OUTPUT)
	String IMGdir;
	@Parameter(type = ItemIO.OUTPUT)
	String ANNdir;
	@Parameter(type = ItemIO.OUTPUT)
	String sep = "--------------------";

	@Parameter(type = ItemIO.OUTPUT)
	double SNR = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double CR = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Heti = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Hetb = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Res = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Sha = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Den = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Cha = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Ove = -1;

	@Parameter(type = ItemIO.OUTPUT)
	double Mit = -1;

	/*
	@Parameter(type = ItemIO.OUTPUT)
	byte Syn = -1;

	@Parameter(type = ItemIO.OUTPUT)
	byte EntLeav = -1;
	*/


	//the GUI path entry function:
	@Override
	public void run()
	{
		try {
			//store the resolution information
			final double[] resolution = new double[3];
			resolution[0] = xRes;
			resolution[1] = yRes;
			resolution[2] = zRes;

			//saves the input paths for the final report table
			IMGdir = imgPath;
			ANNdir = annPath;

			//reference on a shared object that does
			//pre-fetching of data and some common pre-calculation
			ImgQualityDataCache cache = null;

			//do the calculation and retrieve updated cache afterwards
			if (calcSNR)
			{
				final SNR snr = new SNR(log);
				SNR = snr.calculate(imgPath, resolution, annPath, cache);
				cache = snr.getCache();
			}

			if (calcCR)
			{
				final CR cr = new CR(log);
				CR = cr.calculate(imgPath, resolution, annPath, cache);
				cache = cr.getCache();
			}

			if (calcHeti)
			{
				final HETI heti = new HETI(log);
				Heti = heti.calculate(imgPath, resolution, annPath, cache);
				cache = heti.getCache();
			}

			if (calcHetb)
			{
				final HETB hetb = new HETB(log);
				Hetb = hetb.calculate(imgPath, resolution, annPath, cache);
				cache = hetb.getCache();
			}

			if (calcRes)
			{
				final RES res = new RES(log);
				Res = res.calculate(imgPath, resolution, annPath, cache);
				cache = res .getCache();
			}

			if (calcSha)
			{
				final SHA sha = new SHA(log);
				Sha = sha.calculate(imgPath, resolution, annPath, cache);
				cache = sha .getCache();
			}

			if (calcDen)
			{
				final DEN den = new DEN(log);
				Den = den.calculate(imgPath, resolution, annPath, cache);
				cache = den .getCache();
			}

			if (calcCha)
			{
				final CHA cha = new CHA(log);
				Cha = cha.calculate(imgPath, resolution, annPath, cache);
				cache = cha .getCache();
			}

			if (calcOve)
			{
				final OVE ove = new OVE(log);
				Ove = ove.calculate(imgPath, resolution, annPath, cache);
				cache = ove .getCache();
			}

			if (calcMit)
			{
				final MIT mit = new MIT(log);
				Mit = mit.calculate(annPath);
			}

			/*
			if (calcSyn)
			{
				final SYN syn = new SYN(log);
				Syn = syn.calculate(imgPath, resolution, annPath, cache);
				cache = syn .getCache();
			}

			if (calcEntLeav)
			{
				final ENTLEAV entleav = new ENTLEAV(log);
				EntLeav = entleav.calculate(imgPath, resolution, annPath, cache);
				cache = entleav.getCache();
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
