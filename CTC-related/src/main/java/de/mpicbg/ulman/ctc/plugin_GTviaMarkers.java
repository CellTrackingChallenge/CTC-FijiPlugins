/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Vladimír Ulman
 */
package de.mpicbg.ulman.ctc;

import org.scijava.ItemVisibility;
import org.scijava.widget.FileWidget;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.ui.UIService;
import net.imagej.ops.OpService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.scif.img.ImgIOException;
import java.util.List;

import java.util.TreeSet;
import java.text.ParseException;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import org.jhotdraw.samples.svg.gui.ProgressIndicator;
import de.mpicbg.ulman.ctc.Mastodon.util.ButtonHandler;

import java.awt.Button;
import java.awt.Dimension;

import de.mpicbg.ulman.ctc.workers.machineGTViaMarkers_Worker;
import de.mpicbg.ulman.ctc.util.NumberSequenceHandler;

@Plugin(type = Command.class, menuPath = "Plugins>Annotations Merging Tool")
public class plugin_GTviaMarkers implements Command
{

	@Parameter
	private LogService log;

	@Parameter
	private OpService ops;

	@Parameter
	private StatusService statusService;

	@Parameter
	private UIService uiService;


	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String headerA =
		"Please, provide a path to a job specification file (see below), and fill required parameters.";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String headerB =
		"Check the status bar (in the main Fiji window) for hint messages.";

	@Parameter(label = "Merging model:",
			choices = {"Threshold - flat weights",
			           "Threshold - user weights",
			           "Majority - flat weights"},
			           //"SIMPLE","STAPLE"},
			callback = "mergeModelChanged")
	private String mergeModel;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoA = "The job file should list one input filename pattern per line.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoB = "The job file should end with tracking markers filename pattern.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoC = " ";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoD = "Threshold value is required now.";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String fileInfoE =
		 "The filename pattern is a full path to a file that includes XXX or XXXX where "
		+"numbers should be substituted.";

	@Parameter(label = "Job file:", style = FileWidget.OPEN_STYLE,
		description = "Please, make sure that file contains filenames with XXX or XXXX included.",
		callback = "inFileOKAY")
	private File filePath;

	@Parameter(label = "Threshold:", min = "0.0",
		description = "Pixel is merged if there is more-or-equal to this threshold voters supporting it.")
	private float mergeThreshold=1.0f;

	@Parameter(label = "Timepoints to be processed (e.g. 1-9,23,25):",
		description = "Comma separated list of numbers or intervals, interval is number-hyphen-number.",
		validater = "idxChanged")
	private String fileIdxStr = "0-9";

	@Parameter(label = "Output filename pattern:", style = FileWidget.SAVE_STYLE,
		description = "Please, don't forget to include XXX or XXXX into the filename.",
		callback = "outFileOKAY")
	private File outputPath = new File("CHANGE THIS PATH/mergedXXX.tif");


	//citation footer...
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false, label = "Please, refer to:")
	private final String citationFooterA
		= "http://www.fi.muni.cz/~xulman/LABELS/abstract.pdf";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false, label = ":")
	private final String citationFooterB
		= "http://www.fi.muni.cz/~xulman/LABELS/poster.pdf";


	//callbacks:
	@SuppressWarnings("unused")
	private void mergeModelChanged()
	{
		if (mergeModel.startsWith("Threshold - flat"))
		{
			fileInfoA = "The job file should list one input filename pattern per line.";
			fileInfoB = "The job file should end with tracking markers filename pattern.";
			fileInfoC = " ";
			fileInfoD = "Threshold value is required now.";
		}
		else
		if (mergeModel.startsWith("Threshold - user"))
		{
			fileInfoA = "The job file should list one input filename pattern per line";
			fileInfoB = "and space separated single real number weight.";
			fileInfoC = "The job file should end with tracking markers filename pattern.";
			fileInfoD = "Threshold value is required now.";
		}
		else
		if (mergeModel.startsWith("Majority"))
		{
			fileInfoA = "The job file should list one input filename pattern per line.";
			fileInfoB = "The job file should end with tracking markers filename pattern.";
			fileInfoC = " ";
			fileInfoD = "Threshold value is NOT required now.";
		}
		else
		if (mergeModel.startsWith("SIMPLE"))
		{
			fileInfoA = " ";
			fileInfoB = "Don't know yet how to use this model.";
			fileInfoC = " ";
			fileInfoD = " ";
		}
		else
		{
			//STAPLE:
			fileInfoA = " ";
			fileInfoB = "Don't know yet how to use this model.";
			fileInfoC = " ";
			fileInfoD = " ";
		}
	}

	@SuppressWarnings("unused")
	private void idxChanged()
	{
		//check the string is parse-able
		try {
			NumberSequenceHandler.parseSequenceOfNumbers(fileIdxStr,null);
		}
		catch (ParseException e)
		{
			log.warn("Timepoints:\n"+e.getMessage());
			if (!uiService.isHeadless())
				uiService.showDialog("Timepoints:\n"+e.getMessage());
			throw new RuntimeException("Timepoints field is invalid.\n"+e.getMessage());
		}
	}

	//will be also used for sanity checking, thus returns boolean
	private boolean outFileOKAY()
	{
		//check the pattern
		final String name = outputPath.getName();
		if (name == null)
		{
			log.warn("No output filename is given.");
			statusService.showStatus("No output filename is given.");
			return false;
		}
		//does it contain "XXX" and the number of X's is 3 or 4?
		if (name.indexOf("XXX") == -1 || ( (name.lastIndexOf("XXX") - name.indexOf("XXX")) > 1 ))
		{
			log.warn("Filename \""+name+"\" does not contain XXX or XXXX pattern.");
			statusService.showStatus("Filename \""+name+"\" does not contain XXX or XXXX pattern.");
			return false;
		}

		//check the parent folder exists
		final File path = outputPath.getParentFile();
		if (path != null && !path.exists())
		{
			log.warn("Parent folder \""+path.getAbsolutePath()+"\" does not exist.");
			statusService.showStatus("Parent folder \""+path.getAbsolutePath()+"\" does not exist.");
			return false;
		}

		log.info("Filename contains XXX or XXXX pattern, parent folder exists, all good.");
		statusService.showStatus("Filename contains XXX or XXXX pattern, parent folder exists, all good.");
		return true;
	}

	//will be also used for sanity checking, thus returns boolean
	private boolean inFileOKAY()
	{
		//check the job file exists
		if (filePath == null || !filePath.exists())
		{
			log.warn("Job file \""+filePath.getAbsolutePath()+"\" does not exist.");
			statusService.showStatus("Job file \""+filePath.getAbsolutePath()+"\" does not exist.");
			return false;
		}

		//check it has understandable content:
		//is there additional column with weights?
		final boolean weightAvail = mergeModel.startsWith("Threshold - user");

		//read the whole input file
		List<String> job = null;
		try {
			job = Files.readAllLines(Paths.get(filePath.getAbsolutePath()));
		}
		catch (IOException e) {
			log.error("plugin_GTviaMarkers error: "+e);
		}

		int lineNo=0;
		for (String line : job)
		{
			++lineNo;

			//this currently represents the first column/complete line
			String partOne = line;

			//should there be the weight column on this line?
			if (weightAvail && lineNo < job.size())
			{
				//yes, there should be one...
				String[] lineTokens = line.split("\\s+");

				//is there the second column at all?
				if (lineTokens.length == 1)
				{
					log.warn("Missing column with weights on line "+lineNo+".");
					if (!uiService.isHeadless())
					{
						statusService.showStatus("Missing column with weights on line "+lineNo+".");
						uiService.showDialog(    "Missing column with weights on line "+lineNo+".");
					}
					return false;
				}

				//get the first part into the partOne variable
				partOne = new String(); //NB: could be nice to be able to tell the String how much to reserve as we know it
				for (int q=0; q < lineTokens.length-1; ++q)
					partOne += lineTokens[q];

				//is the column actually float-parsable number?
				String partTwo = lineTokens[lineTokens.length-1];
				try {
					Float.parseFloat(partTwo);
				}
				catch (Exception e) {
					log.warn("The weight column \""+partTwo+"\" cannot be parsed as a real number on line "+lineNo+".");
					if (!uiService.isHeadless())
					{
						statusService.showStatus("The weight column \""+partTwo+"\" cannot be parsed as a real number on line "+lineNo+".");
						uiService.showDialog(    "The weight column \""+partTwo+"\" cannot be parsed as a real number on line "+lineNo+".");
					}
					return false;
				}
			}

			//test for presence of the expanding pattern XXX or XXXX
			if (partOne.indexOf("XXX") == -1 || ( (partOne.lastIndexOf("XXX") - partOne.indexOf("XXX")) > 1 ))
			{
				log.warn("Filename \""+partOne+"\" does not contain XXX or XXXX pattern on line "+lineNo+".");
				if (!uiService.isHeadless())
				{
					statusService.showStatus("Filename \""+partOne+"\" does not contain XXX or XXXX pattern on line "+lineNo+".");
					uiService.showDialog(    "Filename \""+partOne+"\" does not contain XXX or XXXX pattern on line "+lineNo+".");
				}
				return false;
			}
		}

		log.info("Job file feels sane.");
		statusService.showStatus("Job file feels sane.");
		return true;
	}

	/** populates Xs in the \e pattern with \e idx, and returns result in a new string,
	    it supports XXX or XXXX */
	String expandFilenamePattern(final String pattern, final int idx)
	{
		//detect position
		int a = pattern.indexOf("XXX");
		int b = pattern.lastIndexOf("XXX");
		//and span
		b = b > a ? 4 : 3;

		String res = pattern.substring(0,a);
		res += String.format(String.format("%c0%dd",'%',b),idx);
		res += pattern.substring(a+b);
		return res;
	}


	//the GUI path entry function:
	@Override
	public void run()
	{
		//check that input file exists,
		//parses it to prepare an array of strings -- a job description,
		//and calls the merging function below -- main()

		//check that input is okay
		if (!inFileOKAY() || !outFileOKAY())
		{
			log.error("plugin_GTviaMarkers error: Input parameters are wrong.");
			if (!uiService.isHeadless())
				uiService.showDialog("There is something wrong with either the job file or output file.");

			return;
		}
		if (!mergeModel.startsWith("Threshold")
		 && !mergeModel.startsWith("Majority"))
		{
			log.error("plugin_GTviaMarkers error: Unsupported merging model.");
			if (!uiService.isHeadless())
				uiService.showDialog("plugin_GTviaMarkers error: Unsupported merging model.");

			return;
		}

		//parses job file (which we know is sane for sure) to prepare an array of strings (an imagej-ops job description)
		//is there additional column with weights?
		final boolean weightAvail = mergeModel.startsWith("Threshold - user");

		//read the whole input file
		List<String> job = null;
		try {
			job = Files.readAllLines(Paths.get(filePath.getAbsolutePath()));
		}
		catch (IOException e) {
			log.error("plugin_GTviaMarkers error: "+e);
		}

		//prepare the output array
		String[] argsPattern = new String[2*job.size()+1]; //= 2*(job.size()-1) +1 +2

		//parse the input job specification file (which we know is sane for sure)
		int lineNo=0;
		for (String line : job)
		{
			//this currently represents the first column/complete line
			String partOne = line;

			//should there be the weight column on this line?
			//are we still on lines where weight column should be handled?
			if (lineNo < (job.size()-1))
			{
				if (weightAvail)
				{
					//yes, there should be one...
					String[] lineTokens = line.split("\\s+");
					//NB: inFileOKAY() was true, so there is the second column

					//get the first part into the partOne variable
					partOne = new String(); //NB: could be nice to be able to tell the String how much to reserve as we know it
					for (int q=0; q < lineTokens.length-1; ++q)
						partOne += lineTokens[q];

					//the weight itself
					argsPattern[2*lineNo +1] = lineTokens[lineTokens.length-1];
				}
				else
				{
					//if user-weights not available, provide own ones
					//(provided we are not parsing the very last line with TRA marker image)
					argsPattern[2*lineNo +1] = "1.0";
				}
			}

			//add the input file item as well
			argsPattern[2*lineNo +0] = partOne;

			++lineNo;
		}

		final float threshold =
			mergeModel.startsWith("Majority") ? (int)((job.size()-1)/2)+1.0f : mergeThreshold;
		argsPattern[2*lineNo -1] = Float.toString(threshold);
		argsPattern[2*lineNo +0] = outputPath.getAbsolutePath();
		//generic job specification is done

		//create an array to hold an "expanded"/instantiated job
		String[] args = new String[argsPattern.length];

		//save the threshold value which is constant all the time
		args[args.length-2] = argsPattern[args.length-2];
		//
		//also weights are constant all the time
		for (int i=1; i < args.length-3; i+=2) args[i] = argsPattern[i];

		//defined here so that finally() block can see them...
		JFrame frame = null;
		Button pbtn = null;
		ProgressIndicator pbar = null;
		ButtonHandler pbtnHandler = null;

		try {
			//parse out the list of timepoints
			TreeSet<Integer> fileIdxList = new TreeSet<>();
			NumberSequenceHandler.parseSequenceOfNumbers(fileIdxStr,fileIdxList);

			//start up the worker class
			final machineGTViaMarkers_Worker Worker
				= new machineGTViaMarkers_Worker(ops,log);

			//prepare a progress bar:
			//init the components of the bar
			frame = uiService.isHeadless() ? null : new JFrame("CTC Merging Progress Bar");
			if (frame != null)
			{
				pbar = new ProgressIndicator("Time points processed: ", "",
						0, fileIdxList.size(), false);
				pbtn = new Button("Stop merging");
				pbtnHandler = new ButtonHandler();
				pbtn.setMaximumSize(new Dimension(150, 40));
				pbtn.addActionListener(pbtnHandler);

				//populate the bar and show it
				frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
				frame.add(pbar);
				frame.add(pbtn);
				frame.setMinimumSize(new Dimension(300, 100));
				frame.setLocationByPlatform(true);
				if (uiService.isVisible()) frame.setVisible(true);
			}

			long ttime = System.currentTimeMillis();

			//iterate over all jobs
			int progresCnt = 0;
			for (Integer idx : fileIdxList)
			{
				if (frame != null)
				{
					pbar.setProgress(progresCnt++);
					if (pbtnHandler.buttonPressed()) break;
				}

				//first populate/expand to get a particular instance of a job
				for (int i=0; i < args.length-2; i+=2)
					args[i] = expandFilenamePattern(argsPattern[i],idx);
				args[args.length-1] = expandFilenamePattern(argsPattern[args.length-1],idx);

				log.info("new job:");
				int i=0;
				for (; i < args.length-3; i+=2)
					log.info(i+": "+args[i]+"  "+args[i+1]);
				for (; i < args.length; ++i)
					log.info(i+": "+args[i]);


				long time = System.currentTimeMillis();
				Worker.work(args);
				time -= System.currentTimeMillis();
				System.out.println("ELAPSED TIME: "+(-time/1000)+" seconds");
			}

			ttime -= System.currentTimeMillis();
			System.out.println("TOTAL ELAPSED TIME: "+(-ttime/1000)+" seconds");
		}
		catch (UnsupportedOperationException | ImgIOException e) {
			log.error("plugin_GTviaMarkers error: "+e);
		}
		catch (ParseException e)
		{
			if (!uiService.isHeadless())
				uiService.showDialog("Timepoints:\n"+e.getMessage());
		}
		finally {
			//hide away the progress bar once the job is done
			if (frame != null)
			{
				pbtn.removeActionListener(pbtnHandler);
				frame.dispose();
			}
		}
	}
}
