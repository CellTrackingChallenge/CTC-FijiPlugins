package org.mastodon.plugin.ctc;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Plugin;

import bdv.viewer.Source;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Util;

import org.scijava.plugin.Parameter;

import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.collection.RefIntMap;
import org.mastodon.collection.RefMaps;

@Plugin( type = Command.class )
public class ExporterPlugin extends ContextCommand
{
	@Parameter
	String outputPath;

	@Parameter
	String filePrefix = "man_track";

	@Parameter
	String filePostfix = ".tif";

	@Parameter
	int fileNoDigits = 3;

	@Parameter
	int timeFrom;

	@Parameter
	int timeTill;

	@Parameter
	Model model;

	@Parameter
	Source<?> imgSource;

	@Override
	public void run()
	{
		//debug
		System.out.println("Output folder is   : "+outputPath);
		System.out.println("Time points span is: "+String.valueOf(timeFrom)+"-"+String.valueOf(timeTill));

		//enumerate output files
		final String outFilenameFormat = String.format("%s%s%s%%0%dd%s", outputPath,File.separator,filePrefix,fileNoDigits,filePostfix);

		//params of output files
		//transformation used with the 1st setup
		final AffineTransform3D coordTransImg2World = new AffineTransform3D();
		imgSource.getSourceTransform(0,0, coordTransImg2World);

		//voxel size = 1/resolution
		if (imgSource.getVoxelDimensions().unit().startsWith("um") == false)
			throw new IllegalArgumentException("Incompatible resolution units used in this project: "+imgSource.getVoxelDimensions().unit());
		final double[] voxelSize = new double[3];
		imgSource.getVoxelDimensions().dimensions(voxelSize);

		//RAI corresponding to the output image
		final RandomAccessibleInterval<?> imgTemplate = imgSource.getSource(0,0);

		final long[] voxelCounts = new long[3]; //REMOVE ME
		imgTemplate.dimensions(voxelCounts);
		final double[] tt = new double[16];
		coordTransImg2World.toArray(tt);
		System.out.println("Output image size  : "+Util.printCoordinates(voxelCounts));
		System.out.println("Output voxel size  : "+Util.printCoordinates(voxelSize));
		System.out.println("Coord transform    : "+Util.printCoordinates(tt));
		{
			final String filename = String.format(outFilenameFormat, outputPath,File.separator,filePrefix,t,filePostfix);
			System.out.println(filename);
		}
	}
}
