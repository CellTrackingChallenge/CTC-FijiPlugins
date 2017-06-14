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
import org.scijava.log.LogService;
import org.scijava.app.StatusService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import net.imagej.ImageJ;
import net.imagej.ops.OpService;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.img.ImgIOException;
import io.scif.img.SCIFIOImgPlus;
import io.scif.img.ImgOpener;
import io.scif.img.ImgSaver;
import net.imglib2.exception.IncompatibleTypeException;

import java.util.List;
import java.util.Vector;
import java.io.IOException;

@Plugin(type = Command.class, menuPath = "Plugins>CTC>Annotations Merging Tool")
public class machineGTViaMarkers implements Command
{

	@Parameter
	private LogService log;

	@Parameter
	private OpService ops;

	@Parameter
	private StatusService statusService;


	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String headerA =
		"Please, provide a path to a job specification file (see below), and fill required parameters.";

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String headerB =
		"Check the status bar (in the main Fiji window) for hint messages.";

	@Parameter(label = "Merging model:",
			choices = {"Threshold - flat weights",
			           //"Threshold - user weights",
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
			return false;
		}

		//check the parent folder exists
		final File path = outputPath.getParentFile();
		if (path != null && !path.exists())
		{
			statusService.showStatus("Parent folder "+path.getAbsolutePath()+" does not exist.");
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
					return false;
				}
			}

			//test for (optional) weight column, if not on the last line
			if (weightAvail && lineNo < job.size())
			{
				if (partNo < 2)
				{
					statusService.showStatus("Missing column with weights on line "+lineNo+".");
					return false;
				}
				if (partNo > 2)
				{
					statusService.showStatus("Detected extra column after weights on line "+lineNo+".");
					return false;
				}
			}
			//no extra columns when weights are not expected
			//no extra columns on the last line (in any case)
			if ((!weightAvail || lineNo == job.size()) && partNo != 1)
			{
				statusService.showStatus("Detected extra column after filename pattern on line "+lineNo+".");
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
			return;
		}
		if (!mergeModel.startsWith("Threshold - flat")
		 && !mergeModel.startsWith("Majority"))
		{
			log.error("machineGTViaMarkers error: Unsupported merging model.");
			return;
		}

		//parses job file (which we know is sane for sure) to prepare an array of strings (an imagej-ops job description)
		//is there additional column with weights?
		//final boolean weightAvail = mergeModel.startsWith("Threshold - user"); //TODO

		//read the whole input file
		List<String> job = null;
		try {
			job = Files.readAllLines(Paths.get(filePath.getAbsolutePath()));
		}
		catch (IOException e) {
			log.error("machineGTViaMarkers error: "+e);
		}

		//prepare the output array
		String[] argsPattern = new String[job.size()+2];
		
		//parse the input job specification file (which we know is sane for sure)
		int lineNo=0;
		for (String line : job)
		{
			++lineNo;

			//read items on the line
			int partNo=0;
			for (String part : line.split("\\s+"))
			{
				++partNo;

				if (partNo == 1) argsPattern[lineNo-1] = part;
				//if (partNo == 2) storeWeight=(Float.valueOf(part)).floatValue(); //TODO
			}
			//if (!weightAvail && lineNo < job.size()) storeWeight=1.0f; //TODO
		}

		final float threshold =
			mergeModel.startsWith("Majority") ? (int)((job.size()-1)/2)+1.0f : mergeThreshold;
		//argsPattern[lineNo] = Float.toString(threshold); //TODO
		argsPattern[lineNo] = Integer.toString((int)threshold);
		argsPattern[lineNo+1] = outputPath.getAbsolutePath();
		//generic job specification is done

		//create an array to hold an "expanded"/instantiated job
		String[] args = new String[argsPattern.length];

		//save the threshold value which is constant all the time
		args[args.length-2] = argsPattern[args.length-2];

		//iterate over all jobs
		for (int idx = fileIdxFrom; idx <= fileIdxTo; ++idx)
		{
			//first populate/expand to get a particular instance of a job
			for (int i=0; i < args.length-2; ++i)
				args[i] = expandFilenamePattern(argsPattern[i],idx);
			args[args.length-1] = expandFilenamePattern(argsPattern[args.length-1],idx);

			log.info("new job:");
			for (int i=0; i < args.length; ++i)
				log.info(i+": "+args[i]);

			try {
				worker(ops,log,args);
			}
			catch (ImgIOException e) {
				log.error("machineGTViaMarkers error: "+e);
			}
		}
	}


	//the CLI path entry function:
	public static void main(final String... args) throws ImgIOException
	{
		//start up our own ImageJ
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(machineGTViaMarkers.class, true);

		//non-head less variant:
		/*
		System.out.println("cauky");
		ij.ui().showUI();
		ij.command().run(machineGTViaMarkers.class, true);
		*/
		System.out.println("TBA");

		//worker(ij.op(),ij.log(),args); -- ask Matthias for a workaround
		//ij.appEvent().quit();
	}


	@SuppressWarnings({ "rawtypes", "unchecked" })
	void worker(final OpService ops, final LogService log, final String... args) throws ImgIOException
	{
		//check the input parameters
		if (args.length < 4)
		{
			//print help
			log.info("Usage: img1 ... TRAimg threshold outImg");
			log.info("All img1 (path to an image file) are TRA marker-wise combined into output outImg.");
			throw new ImgIOException("At least one input image, exactly one marker image and one treshold plus one output image are expected.");
		}

		//container to store the input images
		final Vector<RandomAccessibleInterval<?>> inImgs
			= new Vector<RandomAccessibleInterval<?>>(args.length-3);

		//marker image
		IterableInterval<UnsignedShortType> markerImg = null;

		//now, try to load the input images
		final SCIFIOConfig openingRegime = new SCIFIOConfig();
		openingRegime.imgOpenerSetImgModes(ImgMode.ARRAY);
		SCIFIOImgPlus<?> img = null;

		//load all of them
		for (int i=0; i < args.length-2; ++i)
		{
			try {
				//load the image
				log.info("Reading file: "+args[i]);
				ImgOpener imgOpener = new ImgOpener();
				img = imgOpener.openImgs(args[i],openingRegime).get(0);

				//check the type of the image (the combineGTs plug-in requires RealType<>)
				//TODO this code does not assure that all input images are of the same type
				if (!(img.firstElement() instanceof RealType<?>))
					throw new ImgIOException("Input image voxels must be scalars.");

				//check the dimensions, against the first loaded image
				//(if processing second or later image already)
				for (int d=0; i > 0 && d < img.numDimensions(); ++d)
					if (img.dimension(d) != inImgs.get(0).dimension(d))
						throw new ImgIOException((i+1)+"th image has different size in the "
								+d+"th dimension than the first image.");

				//all is fine, add this one into the input list
				if (i < args.length-3) inImgs.add(img);
				//or, if loading the last image, remember it as the marker image 
				else markerImg = (IterableInterval<UnsignedShortType>)img;
			}
			catch (ImgIOException e) {
				log.error("Error reading file: "+args[i]);
				log.error("Error msg: "+e);
				throw new ImgIOException("Unable to read input file.");
			}
		}

		//parse threshold value
		final UnsignedShortType threshold
			= new UnsignedShortType( Integer.parseInt(args[args.length-2]) );

		//create the output image (TODO: need to use copy() of the first input image?)
		SCIFIOImgPlus<UnsignedShortType> outImg
			= new SCIFIOImgPlus<UnsignedShortType>(
					ops.create().img(inImgs.get(0), new UnsignedShortType()));

		//setup the debug image filename
		String newName = args[args.length-1];
		final int dotSeparatorIdx = newName.lastIndexOf(".");
		newName = new String(newName.substring(0, dotSeparatorIdx)+"__DBG"+newName.substring(dotSeparatorIdx));

		//NB: we have checked that images are of RealType<?> in the loading loop,
		//    so we know we can cast to raw type to be able to call the combineGTs()
		System.out.println("calling general convenience CombineGTsViaMarkers with threshold="+threshold);
		//ops.images().combineGTsViaMarkers((Vector)inImgs, markerImg, threshold, outImg);
		ops.images().combineGTsViaMarkers((Vector)inImgs, markerImg, threshold, outImg, newName);

		try {
			log.info("Saving file: "+args[args.length-1]);
			ImgSaver imgSaver = new ImgSaver();
			imgSaver.saveImg(args[args.length-1], outImg);
		}
		catch (ImgIOException | IncompatibleTypeException e) {
			log.error("Error writing file: "+args[args.length-1]);
			log.error("Error msg: "+e);
			throw new ImgIOException("Unable to write output file.");
		}
	}
}
