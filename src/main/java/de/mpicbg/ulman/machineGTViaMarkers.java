/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import org.scijava.ItemVisibility;
import org.scijava.widget.FileWidget;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.ui.UIService;
import net.imagej.ops.OpService;
import net.imagej.ImageJ;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.scif.img.ImgIOException;

import java.util.List;
import java.io.IOException;

import de.mpicbg.ulman.workers.machineGTViaMarkers_Worker;

@Plugin(type = Command.class, menuPath = "Plugins>CTC>Annotations Merging Tool")
public class machineGTViaMarkers implements Command
{

	@Parameter
	private LogService log;

	@Parameter
	private OpService ops;

	@Parameter
	private StatusService statusService;

	@Parameter
	private UIService uiService;


	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String headerA =
		"Please, provide a path to a job specification file (see below), and fill required parameters.";

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String headerB =
		"Check the status bar (in the main Fiji window) for hint messages.";

	@Parameter(label = "Merging model:",
			choices = {"Threshold - flat weights",
			           "Threshold - user weights",
			           "Majority - flat weights"},
			           //"SIMPLE","STAPLE"},
			callback = "mergeModelChanged")
	private String mergeModel;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String fileInfoA = "The job file should list one input filename pattern per line.";
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String fileInfoB = "The job file should end with tracking markers filename pattern.";
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String fileInfoC = " ";
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String fileInfoD = "Threshold value is required now.";

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String fileInfoE =
		 "The filename pattern is a full path to a file that includes XXX where "
		+"numbers should be substituted.";

	@Parameter(label = "Job file:", style = FileWidget.OPEN_STYLE,
		description = "Please, make sure that file contains filenames with XXX included.",
		callback = "inFileOKAY")
	private File filePath;

	@Parameter(label = "Threshold:", min = "0.0",
		description = "Pixel is merged if there is more-or-equal to this threshold voters supporting it.")
	private float mergeThreshold=1.0f;

	@Parameter(label = "Starting index:",
		description = "The range values are inclusive.",
		callback = "idxFromChanged")
	private int fileIdxFrom;
	@Parameter(label = "Ending index:",
		description = "The range values are inclusive.",
		callback = "idxToChanged")
	private int fileIdxTo;

	@Parameter(label = "Output filename pattern:", style = FileWidget.SAVE_STYLE,
		description = "Please, don't forget to include XXX into the filename.",
		callback = "outFileOKAY")
	private File outputPath = new File("CHANGE THIS PATH/mergedXXX.tif");
	

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
	private void idxFromChanged()
	{
		//non-sense value?
		if (fileIdxFrom < 0) fileIdxFrom = 0;

		//interval broken?
		if (fileIdxTo < fileIdxFrom) fileIdxTo = fileIdxFrom;
	}

	@SuppressWarnings("unused")
	private void idxToChanged()
	{
		//non-sense value?
		if (fileIdxTo < 0) fileIdxTo = 0;

		//interval broken?
		if (fileIdxFrom > fileIdxTo) fileIdxFrom = fileIdxTo;
	}

	//will be also used for sanity checking, thus returns boolean
	private boolean outFileOKAY()
	{
		//check the pattern
		final String name = outputPath.getName();
		if (name != null && (name.lastIndexOf("X") - name.indexOf("X")) != 2)
		{
			statusService.showStatus("Filename "+name+" does not contain XXX pattern.");
			//uiService.showDialog(    "Filename "+name+" does not contain XXX pattern.");
			return false;
		}

		//check the parent folder exists
		final File path = outputPath.getParentFile();
		if (path != null && !path.exists())
		{
			statusService.showStatus("Parent folder "+path.getAbsolutePath()+" does not exist.");
			//uiService.showDialog(    "Parent folder "+path.getAbsolutePath()+" does not exist.");
			return false;
		}

		statusService.showStatus("Filename contains XXX pattern, parent folder exists, all good.");
		return true;
	}

	//will be also used for sanity checking, thus returns boolean
	private boolean inFileOKAY()
	{
		//check the job file exists
		if (filePath == null || !filePath.exists())
		{
			statusService.showStatus("Job file "+filePath.getAbsolutePath()+" does not exist.");
			//uiService.showDialog(    "Job file "+filePath.getAbsolutePath()+" does not exist.");
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
			log.error("machineGTViaMarkers error: "+e);
		}

		int lineNo=0;
		for (String line : job)
		{
			++lineNo;

			//read items on the line
			int partNo=0;
			for (String part : line.split("\\s+"))
			{
				++partNo;

				//test for presence of the expanding pattern
				if (partNo == 1 && (part.lastIndexOf("X") - part.indexOf("X")) != 2)
				{
					statusService.showStatus("Filename "+part+" does not contain XXX pattern on line "+lineNo+".");
					uiService.showDialog(    "Filename "+part+" does not contain XXX pattern on line "+lineNo+".");
					return false;
				}
				if (partNo == 2 && weightAvail && lineNo < job.size())
				{
					//is the column actually float-parsable number?
					try {
						Float.parseFloat(part);
					}
					catch (Exception e) {
						statusService.showStatus("The weight column "+part+" cannot be parsed as a real number on line "+lineNo+".");
						uiService.showDialog(    "The weight column "+part+" cannot be parsed as a real number on line "+lineNo+".");
						return false;
					}
				}
			}

			//test for (optional) weight column, if not on the last line
			if (weightAvail && lineNo < job.size())
			{
				if (partNo < 2)
				{
					statusService.showStatus("Missing column with weights on line "+lineNo+".");
					uiService.showDialog(    "Missing column with weights on line "+lineNo+".");
					return false;
				}
				if (partNo > 2)
				{
					statusService.showStatus("Detected extra column after weights on line "+lineNo+".");
					uiService.showDialog(    "Detected extra column after weights on line "+lineNo+".");
					return false;
				}
			}
			//no extra columns when weights are not expected
			//no extra columns on the last line (in any case)
			if ((!weightAvail || lineNo == job.size()) && partNo != 1)
			{
				statusService.showStatus("Detected extra column after filename pattern on line "+lineNo+".");
				uiService.showDialog(    "Detected extra column after filename pattern on line "+lineNo+".");
				return false;
			}
		}

		statusService.showStatus("Job file feels sane.");
		return true;
	}

	///populates Xs in the \e pattern with \e idx, and returns result in a new string
	String expandFilenamePattern(final String pattern, final int idx)
	{
		//detect position
		int a = pattern.indexOf("X");
		int b = pattern.lastIndexOf("X");
		//and span
		b = b-a+1;

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
			log.error("machineGTViaMarkers error: Input parameters are wrong.");
			uiService.showDialog("There is something wrong with either the job file or output file.");
			return;
		}
		if (!mergeModel.startsWith("Threshold")
		 && !mergeModel.startsWith("Majority"))
		{
			log.error("machineGTViaMarkers error: Unsupported merging model.");
			uiService.showDialog("machineGTViaMarkers error: Unsupported merging model.");
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
			log.error("machineGTViaMarkers error: "+e);
		}

		//prepare the output array
		String[] argsPattern = new String[2*job.size()+1]; //= 2*(job.size()-1) +1 +2
		
		//parse the input job specification file (which we know is sane for sure)
		int lineNo=0;
		for (String line : job)
		{
			//read items on the line
			int partNo=0;
			for (String part : line.split("\\s+"))
			{
				argsPattern[2*lineNo +partNo] = part;
				++partNo;
			}
			++lineNo;

			//if user-weights not available, provide own ones
			//(provided we are not parsing the very last line with TRA marker image)
			if (!weightAvail && lineNo < job.size()) argsPattern[2*(lineNo-1) +1] = "1.0";
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

		try {
			//start up the worker class
			final machineGTViaMarkers_Worker Worker
				= new machineGTViaMarkers_Worker(ops,log);

			//iterate over all jobs
			for (int idx = fileIdxFrom; idx <= fileIdxTo; ++idx)
			{
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

					Worker.work(args);
			}
		}
		catch (ImgIOException e) {
			log.error("machineGTViaMarkers error: "+e);
		}
	}


	//the CLI path entry function:
	public static void main(final String... args)
	{
		//head less variant:
		//start up our own ImageJ without GUI
		final ImageJ ij = new net.imagej.ImageJ();
		//ij.ui().showUI();
		//ij.command().run(machineGTViaMarkers.class, true);

		try {
			//start up the worker class
			final machineGTViaMarkers_Worker Worker
				= new machineGTViaMarkers_Worker(ij.op(),ij.log());

			//do the job
			Worker.work(args);
		}
		catch (ImgIOException e) {
			ij.log().error("machineGTViaMarkers error: "+e);
		}

		//and quit
		ij.appEvent().quit();
	}
}
