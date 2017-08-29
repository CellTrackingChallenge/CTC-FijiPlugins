/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 */
package de.mpicbg.ulman.workers;

import org.scijava.log.LogService;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.FinalDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import io.scif.img.ImgIOException;

import java.util.Vector;
import java.util.HashSet;
import java.util.HashMap;

public class ImgQualityDataCache
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public ImgQualityDataCache(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}

	///GT and RES paths combination for which this cache is valid, null means invalid
	private String imgPath = null;
	///GT and RES paths combination for which this cache is valid, null means invalid
	private String annPath = null;

	///reference-based-only check if the parameters are those on which this cache was computed
	public boolean validFor(final String _imgPath, final String _annPath)
	{
		return (imgPath == _imgPath && annPath == _annPath);
	}

	///if proper area/surface estimates should be computed
	public boolean doSurfaces = false;


	// ----------- the common upper stage essentially starts here -----------
	//auxiliary data:

	///representation of resolution, no dimensionality restriction (unlike in GUI)
	private double[] resolution = null;

	public void setResolution(final double[] _res)
	{
		//check if resolution data is sane
		if (_res == null)
			throw new IllegalArgumentException("No pixel resolution data supplied!");
		for (double r : _res)
			if (r <= 0.0)
				throw new IllegalArgumentException("Negative or zero resolution supplied!");

		//copy the supplied resolution to the class structures,
		resolution = new double[_res.length];
		for (int n=0; n < resolution.length; ++n)
			resolution[n] = _res[n];
	}

	//the structure to interchange results from the extractObjectDistance()
	private class ObjectDescription
	{
		int otherMarker;
		float distance;
	}
	private ObjectDescription objDistance = new ObjectDescription();

	//"time saver" to prevent allocating it over and over again:
	//for storing coordinates
	private int[] pos = null;
	private int[] box = null;
	private float[] boxDistances = null; //only for isotropic boxes 3x3x...x3
	private FinalDimensions imgSize = null;
	ArrayImgFactory<FloatType> imgFactory = new ArrayImgFactory<>();

	/**
	 * Representation of average & std. deviations within individual
	 * foreground masks.
	 * Usage: avgFG[timePoint].get(labelID) = averageIntensityValue
	 */
	public final Vector<HashMap<Integer,Double>> avgFG = new Vector<>(1000,100);
	/// Similar to this.avgFG
	public final Vector<HashMap<Integer,Double>> stdFG = new Vector<>(1000,100);

	/// Stores NUMBER OF VOXELS (not a real volume) of the FG masks at time points.
	public final Vector<HashMap<Integer,Long>> volumeFG = new Vector<>(1000,100);

	/// Converts this.volumeFG values (no. of voxels) into a real volume (in cubic micrometers)
	public double getRealVolume(final long vxlCnt)
	{
		double v = (double)vxlCnt;
		for (double r : this.resolution) v *= r;
		return (v);
	}

	/// Stores REAL SURFACE (in square micrometers) of the FG masks at time points.
	public final Vector<HashMap<Integer,Double>> surfaceFG = new Vector<>(1000,100);

	/**
	 * Stores how many voxels are there in the intersection of masks of the same
	 * marker at time point and previous time point.
	 */
	public final Vector<HashMap<Integer,Long>> overlapFG = new Vector<>(1000,100);

	/**
	 * Representation of average & std. deviations of background region.
	 * There is only one background marker expected in the images.
	 */
	public final Vector<Double> avgBG = new Vector<>(1000,100);
	/// Similar to this.avgBG
	public final Vector<Double> stdBG = new Vector<>(1000,100);

	//---------------------------------------------------------------------/
	//aux data fillers -- merely markers' properties calculator

	/**
	 * The cursor \e imgPosition points into the raw image at voxel whose counterparting voxel
	 * in the \e imgFGcurrent image stores the first (in the sense of \e imgPosition internal
	 * sweeping order) occurence of the marker that is to be processed with this function.
	 *
	 * This function pushes into global data at the specific \e time .
	 */
	private <T extends RealType<T>>
	void extractFGObjectStats(final Cursor<T> imgPosition, final int time, //who: "object" @ time
		final RandomAccessibleInterval<UnsignedShortType> imgFGcurrent,     //where: input masks
		final RandomAccessibleInterval<UnsignedShortType> imgFGprevious)
	{
		//working pointers into the mask images
		final RandomAccess<UnsignedShortType> fgCursor = imgFGcurrent.randomAccess();

		//obtain the ID of the processed object
		//NB: imgPosition points already at sure existing voxel
		fgCursor.setPosition(imgPosition);
		final int marker = fgCursor.get().getInteger();

		//init aux variables:
		double intSum = 0.; //for single-pass calculation of mean and variance
		double int2Sum = 0.;
		//according to: https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Computing_shifted_data
		//to fight against numerical issues we introduce a "value shifter",
		//which we can already initiate with an "estimate of mean" which we
		//derive from the object's first spotted voxel value
		//NB: imgPosition points already at sure existing voxel
		final double valShift=imgPosition.get().getRealDouble();

		//the voxel counter (for volume)
		long vxlCnt = 0L;

		//working copy of the input cursor, this one drives the image sweeping
		//sweep the image and search for this object/marker
		final Cursor<T> rawCursor = imgPosition.copyCursor();
		while (rawCursor.hasNext())
		{
			rawCursor.next();
			fgCursor.setPosition(rawCursor);

			if (fgCursor.get().getInteger() == marker)
			{
				//processing voxel that belongs to the current FG object:
				//increase current volume
				++vxlCnt;

				final double val = rawCursor.get().getRealDouble();
				intSum += (val-valShift);
				int2Sum += (val-valShift) * (val-valShift);
			}
		}
		//must hold: vxlCnt > 1 (otherwise ClassifyLabels wouldn't call this function)

		//finish processing of the FG objects stats:
		//mean intensity
		avgFG.get(time).put(marker, (intSum / (double)vxlCnt) + valShift );

		//variance
		int2Sum -= (intSum*intSum/(double)vxlCnt);
		int2Sum /= (double)vxlCnt;
		//
		//std. dev.
		stdFG.get(time).put(marker, Math.sqrt(int2Sum) );

		//voxel count
		volumeFG.get(time).put(marker, vxlCnt );

		//TODO: flag to be set from GUI run() depending on calcSha == true
		//
		//call dedicated function to calculate surface in real coordinates,
		//real area/surface
		if (doSurfaces)
			surfaceFG.get(time).put(marker, 999.9 ); //TODO replace 999 with some function call
		else
			surfaceFG.get(time).put(marker, -1.0 );

		//also process the "overlap feature" (if the object was found in the previous frame)
		if (time > 0 && surfaceFG.get(time-1).get(marker) != null)
			overlapFG.get(time).put(marker,
				measureObjectsOverlap(imgPosition,imgFGcurrent, marker,imgFGprevious) );
	}


	/**
	 * The functions counts how many times the current marker (see below) in the image \e imgFGcurrent
	 * co-localizes with marker \e prevID in the image \e imgFGprevious. This number is returned.
	 *
	 * The cursor \e imgPosition points into the raw image at voxel whose counterparting voxel
	 * in the \e imgFGcurrent image stores the first (in the sense of \e imgPosition internal
	 * sweeping order) occurence of the marker that is to be processed with this function.
	 */
	private <T extends RealType<T>>
	long measureObjectsOverlap(final Cursor<T> imgPosition, //pointer to the raw image -> gives current FG ID
	                           final RandomAccessibleInterval<UnsignedShortType> imgFGcurrent, //FG mask
									   final int prevID, //prev FG ID
	                           final RandomAccessibleInterval<UnsignedShortType> imgFGprevious) //FG mask
	{
		//working copy of the input cursor, this one drives the image sweeping
		final Cursor<T> rawCursor = imgPosition.copyCursor();

		//working pointers into the (current and previous) object masks
		final RandomAccess<UnsignedShortType> fgCursor = imgFGcurrent.randomAccess();
		final RandomAccess<UnsignedShortType> prevFgCursor = imgFGprevious.randomAccess();

		//obtain the ID of the processed object
		//NB: imgPosition points already at sure existing voxel
		//NB: rawCursor is as if newly created cursor, i.e., now it points before the image
		fgCursor.setPosition(imgPosition);
		final int marker = fgCursor.get().getInteger();

		//return value...
		long count = 0;

		while (rawCursor.hasNext())
		{
			rawCursor.next();
			fgCursor.setPosition(rawCursor);
			prevFgCursor.setPosition(rawCursor);

			if (fgCursor.get().getInteger() == marker && prevFgCursor.get().getInteger() == prevID)
				++count;
		}

		return(count);
	}


	/**
	 * The \e curMarker represents the marker whose distance to nearest
	 * neighbor is to be calculated.
	 *
	 * It dilates (with 3x3x... SE) until it hits some other marker and returns number
	 * of iterations required. The iterations are however limited with \e maxIters.
	 */
	private <T extends IntegerType<T>>
	void extractObjectDistance(final Img<T> img, final int curMarker,
	                           final ObjectDescription objDistance,
	                           final int maxIters)
	//TODO DEBUG REMOVE ME
	//throws Exception
	{
		//overlay over the original input image with special marker boundary
		T specialBorderMarker = img.firstElement().createVariable();
		specialBorderMarker.setInteger(-1);
		final int borderMarker = specialBorderMarker.getInteger(); //short-cut...

		ExtendedRandomAccessibleInterval<T,Img<T>> extImg
			= Views.extendValue(img, specialBorderMarker);

		//working (float-type) "copies" of the input image
		final Img<FloatType> dilIgA = imgFactory.create(imgSize, new FloatType());
		final Img<FloatType> dilIgB = imgFactory.create(imgSize, new FloatType());

		//overlays over the working copies with extended boundary
		ExtendedRandomAccessibleInterval<FloatType,Img<FloatType>> dilImgA
			= Views.extendValue(dilIgA, new FloatType());
		ExtendedRandomAccessibleInterval<FloatType,Img<FloatType>> dilImgB
			= Views.extendValue(dilIgB, new FloatType());

		//setup necessary pointers for the tmp images
		final Cursor<FloatType> sweepCursorA = dilIgA.localizingCursor();
		final Cursor<FloatType> sweepCursorB = dilIgB.localizingCursor();
		final RandomAccess<FloatType> outCursorA = dilImgA.randomAccess();
		final RandomAccess<FloatType> outCursorB = dilImgB.randomAccess();

		//prepare the initial working image
		Cursor<T> imgCursor = img.localizingCursor();
		RandomAccess<FloatType> outCursor = outCursorA;
		while (imgCursor.hasNext())
		{
			if (imgCursor.next().getInteger() == curMarker)
			{
				outCursor.setPosition(imgCursor);
				outCursor.get().setReal(1.f);
			}
		}
		imgCursor = null;
		//TODO DEBUG REMOVE ME
		/*
		String fileName = String.format("%s_t%03d_label%d_dilated0.tif",
			defaultOutputPrefix,currentFrame,curMarker);
		try {
			ij.log().info("Saving file: "+fileName);
			ImgSaver imgSaver = new ImgSaver();
			imgSaver.saveImg(fileName, dilIgA);
		}
		catch (ImgIOException | IncompatibleTypeException e) {
			ij.log().error("Error writing file: "+fileName);
			ij.log().error("Error msg: "+e);
		}
		*/

		//current working cursors: "sweep" scans the input image, "out" checks the input image
		boolean AisInput = true;
		Cursor<FloatType> sweepCursor = sweepCursorA;
		outCursor = outCursorB;
		RandomAccess<T> inCursor = extImg.randomAccess();

		boolean hasHit = false;
		int closestMarker = 0;
		float closestDist = Float.MAX_VALUE;

		int iters=0;
		do
		{
			//dilate and check if we are running into somebody
			//NB: always finish the whole round!
			sweepCursor.reset();
			while (sweepCursor.hasNext())
			{
				//currently observed distance at the currently examined voxel
				float curDist = sweepCursor.next().getRealFloat();

				//check we are over "any already touched" voxel:
				//NB: should be faster than checking if our voxel is a neighbor to "any touched" voxel
				if (curDist > 0.f)
				{
					sweepCursor.localize(pos);
					//reportCoordinate("found marker "+curMarker+" at: ");

					//BOX STRUCTURING ELEMENT -- APPROXIMATES L2 DISTANCES (Eucledian metric)

					//now check its neighbors..., and update hasHit possibly
					//NB: always finish the whole round!
					sweepBox(true); //true means "do init"
					do
					{
						inCursor.setPosition(pos);
						outCursor.setPosition(pos);

						//First, update the distance value at the current "neighbor" voxel:
						//so far best distance stored in the output voxel
						final float neigDist = outCursor.get().getRealFloat();

						//update distance for the current inside-box position
						/*
						float offsetDist = 0;
						for (int i=0; i < box.length; ++i)
							offsetDist += (float)box[i]*(float)box[i];
						offsetDist = (float)Math.sqrt(offsetDist);
						*/

						//NB: this works only for isotropic boxes 3x3x...x3
						int nonZeroAxesCnt = 0;
						for (int i=0; i < box.length; ++i)
							nonZeroAxesCnt += box[i]&1;
						final float offsetDist = boxDistances[nonZeroAxesCnt];

						//first condition: is there any distance stored already?
						//second condition: would a move from the current position
						//improve currently saved distance in the output voxel?
						if (neigDist == 0. || neigDist > curDist+offsetDist)
							outCursor.get().setReal(curDist+offsetDist);

						//Second, check we haven't run into another object (marker)
						int examinedMarker = inCursor.get().getInteger();
						if (examinedMarker > 0 && examinedMarker != curMarker && examinedMarker != borderMarker)
						{
							hasHit = true;
							curDist = outCursor.get().getRealFloat(); //get fresh distance value
							//reportCoordinate(curMarker+" found his neighbor "+examinedMarker+" at distance "+curDist+" at: ");

							if (curDist < closestDist)
							{
								closestDist = curDist;
								closestMarker = examinedMarker;
							}
						}
					}
					while (sweepBox(false));
				} //over "already discovered" voxel
			} //input image sweeping

			//flip the intput/output image sense
			if (AisInput)
			{
				//make B input
				sweepCursor = sweepCursorB;
				outCursor = outCursorA;
				AisInput=false;

				//TODO DEBUG REMOVE ME
				/*
				fileName = String.format("%s_t%03d_label%d_dilated%d.tif",
					defaultOutputPrefix,currentFrame,curMarker,iters+1);
				try {
					ij.log().info("Saving file: "+fileName);
					ImgSaver imgSaver = new ImgSaver();
					imgSaver.saveImg(fileName, dilIgB);
				}
				catch (ImgIOException | IncompatibleTypeException e) {
					ij.log().error("Error writing file: "+fileName);
					ij.log().error("Error msg: "+e);
				}
				*/
			}
			else
			{
				//make A input
				sweepCursor = sweepCursorA;
				outCursor = outCursorB;
				AisInput=true;

				//TODO DEBUG REMOVE ME
				/*
				fileName = String.format("%s_t%03d_label%d_dilated%d.tif",
					defaultOutputPrefix,currentFrame,curMarker,iters+1);
				try {
					ij.log().info("Saving file: "+fileName);
					ImgSaver imgSaver = new ImgSaver();
					imgSaver.saveImg(fileName, dilIgA);
				}
				catch (ImgIOException | IncompatibleTypeException e) {
					ij.log().error("Error writing file: "+fileName);
					ij.log().error("Error msg: "+e);
				}
				*/
			}

			//calculates truly a number of iterations (for now)
			++iters;
		}
		while (!hasHit && iters < maxIters);

		//TODO DEBUG REMOVE ME
		/*
		//Exports only the situation at final iteration
		String fileName = String.format("%s_t%03d_label%d_dilated%d.tif",
			defaultOutputPrefix,currentFrame,curMarker,iters);
		try {
			ij.log().info("Saving file: "+fileName);
			ImgSaver imgSaver = new ImgSaver();
			if (AisInput)
				imgSaver.saveImg(fileName, dilIgA);
			else
				imgSaver.saveImg(fileName, dilIgB);
		}
		catch (ImgIOException | IncompatibleTypeException e) {
			ij.log().error("Error writing file: "+fileName);
			ij.log().error("Error msg: "+e);
		}
		*/

		//set up the return values
		if (hasHit)
		{
			objDistance.otherMarker = closestMarker;
			objDistance.distance = closestDist;
			log.info(curMarker+" found his neighbor "+closestMarker+" at distance "+closestDist+" isotropic voxels.");
		}
		else
		{
			objDistance.otherMarker = -1;
			objDistance.distance = (float)maxIters;
			log.info(curMarker+" has not found his neighbor with in "+maxIters+" iterations.");
		}
	}

	private boolean sweepBox(final boolean init)
	{
		if (init == true)
		{
			for (int i=0; i < box.length; ++i)
			{
				box[i]=-1;
				--pos[i];
			}
			return (true);
		}
		else
		{
			int index=box.length-1;
			while (index >= 0)
			{
				++box[index];
				++pos[index];
				if (box[index] == 2)
				{
					box[index]=-1;
					pos[index]-=3;
					--index;
				}
				else return (true);
			}
		}
		return (false);
	}


	///reports coordinate stored in the internal attribute this.pos[]
	@SuppressWarnings("unused")
	private void reportCoordinate(final String msg)
	{
		if (pos.length == 3)
			System.out.println(msg+"("+pos[0]+","+pos[1]+","+pos[2]+")");
		else
			System.out.println(msg+"("+pos[0]+","+pos[1]+")");
	}


	public void ClassifyLabels(final int time,
	                           IterableInterval<UnsignedShortType> imgRaw,
	                           RandomAccessibleInterval<UnsignedByteType> imgBG,
	                           Img<UnsignedShortType> imgFG,
	                           RandomAccessibleInterval<UnsignedShortType> imgFGprev)
	{
		//uses resolution from the class internal structures, check it is set already
		if (resolution == null)
			throw new IllegalArgumentException("No pixel resolution data is available!");
		//assume that resolution is sane

		//check we have a resolution data available for every dimension
		if (imgRaw.numDimensions() > resolution.length)
			throw new IllegalArgumentException("Raw image has greater dimensionality"
				+" than the available resolution data.");

		//check the sizes of the images
		if (imgRaw.numDimensions() != imgFG.numDimensions())
			throw new IllegalArgumentException("Raw image and FG label image"
				+" are not of the same dimensionality.");
		if (imgRaw.numDimensions() != imgBG.numDimensions())
			throw new IllegalArgumentException("Raw image and BG label image"
				+" are not of the same dimensionality.");

		for (int n=0; n < imgRaw.numDimensions(); ++n)
			if (imgRaw.dimension(n) != imgFG.dimension(n))
				throw new IllegalArgumentException("Raw image and FG label image"
					+" are not of the same size.");
		for (int n=0; n < imgRaw.numDimensions(); ++n)
			if (imgRaw.dimension(n) != imgBG.dimension(n))
				throw new IllegalArgumentException("Raw image and BG label image"
					+" are not of the same size.");

		//.... populate the internal structures ....
		//first, frame-related stats variables:
		long volBGvoxelCnt = 0L;
		long volFGvoxelCnt = 0L;
		long volFGBGcollisionVoxelCnt = 0L;

		double intSum = 0.; //for mean and variance
		double int2Sum = 0.;
		//see extractFGObjectStats() for explanation of this variable
		double valShift=-1.;

		//sweeping variables:
		final Cursor<UnsignedShortType> rawCursor = imgRaw.localizingCursor();
		final RandomAccess<UnsignedByteType> bgCursor = imgBG.randomAccess();
		final RandomAccess<UnsignedShortType> fgCursor = imgFG.randomAccess();

		while (rawCursor.hasNext())
		{
			//update cursors...
			rawCursor.next();
			bgCursor.setPosition(rawCursor);
			fgCursor.setPosition(rawCursor);

			//analyze background voxels
			if (bgCursor.get().getInteger() > 0)
			{
				if (fgCursor.get().getInteger() > 0)
				{
					//found colliding BG voxel, exclude it from BG stats
					++volFGBGcollisionVoxelCnt;
				}
				else
				{
					//found non-colliding BG voxel, include it for BG stats
					++volBGvoxelCnt;

					final double val = rawCursor.get().getRealDouble();
					if (valShift == -1) valShift = val;

					intSum += (val-valShift);
					int2Sum += (val-valShift) * (val-valShift);
				}
			}
			if (fgCursor.get().getInteger() > 0)
				++volFGvoxelCnt; //found FG voxel, update FG stats
		}

		//report the "occupancy stats"
		log.info("Frame at time "+time+" overview:");
		final long imgSize = imgRaw.size();
		log.info("all FG voxels           : "+volFGvoxelCnt+" ( "+100.0*(double)volFGvoxelCnt/imgSize+" %)");
		log.info("pure BG voxels          : "+volBGvoxelCnt+" ( "+100.0*(double)volBGvoxelCnt/imgSize+" %)");
		log.info("BG&FG overlapping voxels: "+volFGBGcollisionVoxelCnt+" ( "+100.0*(double)volFGBGcollisionVoxelCnt/imgSize+" %)");
		final long untouched = imgSize - volFGvoxelCnt - volBGvoxelCnt;
		log.info("not annotated voxels    : "+untouched+" ( "+100.0*(double)untouched/imgSize+" %)");

		//finish processing of the BG stats of the current frame
		if (volBGvoxelCnt > 0)
		{
			//great, some pure-background voxels have been found
			avgBG.add( (intSum / (double)volBGvoxelCnt) + valShift );

			int2Sum -= (intSum*intSum/(double)volBGvoxelCnt);
			int2Sum /= (double)volBGvoxelCnt;
			stdBG.add( Math.sqrt(int2Sum) );
		}
		else
		{
			log.info("Warning: Background annotation has no pure background voxels.");
			avgBG.add( 0.0 );
			stdBG.add( 0.0 );
		}

		//now, sweep the image, detect all labels and calculate & save their properties
		//
		//set to remember already discovered labels
		//(with initial capacity for 1000 labels)
		HashSet<Integer> mDiscovered = new HashSet<Integer>(1000);

		//prepare the per-object data structures
		avgFG.add( new HashMap<>() );
		stdFG.add( new HashMap<>() );
		volumeFG.add( new HashMap<>() );
		surfaceFG.add( new HashMap<>() );
		overlapFG.add( new HashMap<>() );

		rawCursor.reset();
		while (rawCursor.hasNext())
		{
			//update cursors...
			rawCursor.next();
			fgCursor.setPosition(rawCursor);

			//analyze foreground voxels
			final int curMarker = fgCursor.get().getInteger();
			if ( curMarker > 0 && (!mDiscovered.contains(curMarker)) )
			{
				//found not-yet-processed FG voxel,
				//that means: found not-yet-processed FG object
				extractFGObjectStats(rawCursor, time, imgFG, imgFGprev);
				extractObjectDistance(imgFG,curMarker, objDistance, 50);

				//mark the object (and all its voxels consequently) as processed
				mDiscovered.add(curMarker);
			}
		}
	}

	//---------------------------------------------------------------------/
	/**
	 * Measure calculation happens in two stages. The first/upper stage does
	 * data pre-fetch and calculations to populate the TrackDataCache.
	 * TrackDataCache.calculate() actually does this job. The sort of
	 * calculations is such that the other measures could benefit from
	 * it and re-use it (instead of loading images again and doing some
	 * calculations again), and the results are stored in the cache.
	 * The second/bottom stage is measure-specific. It basically finishes
	 * the measure calculation procedure, possible using data from the cache.
	 *
	 * This function computes the common upper stage of measures.
	 */
	public void calculate(final String imgPath, final double[] resolution,
	                      final String annPath)
	throws IOException, ImgIOException
	{
		log.info("IMG path: "+imgPath);
		log.info("ANN path: "+annPath);
		//DEBUG//
		log.info("Computing the common upper part...");

		//test and save the given resolution
		setResolution(resolution);

		//we gonna re-use image loading functions...
		final TrackDataCache tCache = new TrackDataCache(log);

		//iterate through the RAW images folder and read files, one by one,
		//find the appropriate file in the annotations folders,
		//and call ClassifyLabels() for every such tripple,
		//
		//check also previous frame for overlap size
		Img<UnsignedShortType> imgFGprev = null;
		//
		int time = 0;
		while (Files.isReadable(
			new File(String.format("%s/t%03d.tif",imgPath,time)).toPath()))
		{
			//read the image tripple (raw image, FG labels, BG label)
			Img<UnsignedShortType> img
				= tCache.ReadImage(String.format("%s/t%03d.tif",imgPath,time));

			Img<UnsignedShortType> imgFG
				= tCache.ReadImage(String.format("%s/TRA/man_track%03d.tif",annPath,time));

			Img<UnsignedByteType> imgBG
				= tCache.ReadImageG8(String.format("%s/BG/mask%03d.tif",annPath,time));

			//time to allocate helper variables?
			if (time == 0)
			{
				//positions and box sweeping:
				pos = new int[imgFG.numDimensions()];
				box = new int[imgFG.numDimensions()];

				//pre-calculating distances with box (any box point from box centre)
				//based on how many non-zero elements is available in a vector
				//that points at any box point
				//NB: hard-fixed for up to 10-dimensional image
				//NB: this works only for isotropic boxes 3x3x...x3
				boxDistances = new float[10];
				for (int i=1; i < 10; ++i)
					boxDistances[i] = (float)Math.sqrt((double)i);

				//creating tmp images of the right size:
				long[] dims = new long[imgFG.numDimensions()];
				imgFG.dimensions(dims);
				imgSize = new FinalDimensions(dims);
				dims = null;
			}

			ClassifyLabels(time, img, imgBG, imgFG, imgFGprev);

			imgFGprev = null; //be explicit that we do not want this in memory anymore
			imgFGprev = imgFG;
			++time;

			//to be on safe side (with memory)
			img = null;
			imgFG = null;
			imgBG = null;
		}
		imgFGprev = null;

		if (time == 0)
			throw new IllegalArgumentException("No raw image was found!");

		if (volumeFG.size() != time)
			throw new IllegalArgumentException("Internal consistency problem with FG data!");

		if (avgBG.size() != time)
			throw new IllegalArgumentException("Internal consistency problem with BG data!");

		//now that we got here, note for what data
		//this cache is valid, see validFor() above
		this.imgPath = imgPath;
		this.annPath = annPath;
	}
}
