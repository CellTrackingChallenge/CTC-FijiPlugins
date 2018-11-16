package org.mastodon.plugin.ctc;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;

import bdv.viewer.Source;
import io.scif.img.ImgSaver;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.img.Img;
import net.imglib2.Cursor;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.collection.RefIntMap;
import org.mastodon.collection.RefMaps;

@Plugin( type = Command.class )
public class ImporterPlugin <T extends NativeType<T> & RealType<T>>
extends ContextCommand
{
	// ----------------- where is the CTC-formated result -----------------
	@Parameter
	String inputPath;

	@Parameter
	String filePrefix = "man_track";

	@Parameter
	String filePostfix = ".tif";

	@Parameter
	int fileNoDigits = 3;

	@Parameter
	String txtFile = "man_track.txt";

	// ----------------- what is currently displayed in the project -----------------
	@Parameter
	Source<?> imgSource;

	private final int viewNo = 0;
	private final int viewMipLevel = 0;

	// ----------------- where to store the result -----------------
	@Parameter
	Model model;

	@Parameter
	int timeFrom;

	@Parameter
	int timeTill;

	public ImporterPlugin()
	{
		//now empty...
	}


	@Override
	public void run()
	{
		//info or error report
		logServiceRef = this.getContext().getService(LogService.class).log();

		//debug report
		logServiceRef.info("Span of admissible time points is: "+String.valueOf(timeFrom)+"-"+String.valueOf(timeTill));
		logServiceRef.info("Input folder is                  : "+inputPath);

		//aux stuff to create and name the output files
		final String inImgFilenameFormat = String.format("%s%s%s%%0%dd%s",
			inputPath,File.separator,filePrefix,fileNoDigits,filePostfix);



		logServiceRef.info("Done.");
	}


	//some shortcut variables worth remembering
	private int outImgDims = -1;
	private long[] spotMin,spotMax; //image coordinates (in voxel units)
	private double[] radii;         //BBox corners relative to spot's center
	private RealPoint coord;        //aux tmp coordinate
	private LogService logServiceRef;

	public
	String printRealInterval(final RealInterval ri)
	{
		return "["+ri.realMin(0)+","+ri.realMin(1)+","+ri.realMin(2)+"] <-> ["+ri.realMax(0)+","+ri.realMax(1)+","+ri.realMax(2)+"]";
	}
}
