/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Vladimír Ulman
 */
package de.mpicbg.ulman.ctc.workers;

//import net.imagej.ops.Ops;
//import net.imagej.ops.special.computer.AbstractBinaryComputerOp;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.view.Views;
import net.imglib2.FinalInterval;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.plugin.Parameter;
//import org.scijava.plugin.Plugin;

import io.scif.img.ImgIOException;
import io.scif.img.ImgSaver;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
import java.util.HashSet;
import java.util.HashMap;
import net.imagej.ops.OpService;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.roi.labeling.*;
import net.imglib2.loops.LoopBuilder;

/**
 * Every voxel in the output image is set with the number of non-zero
 * corresponding voxels from the input set of images.
 * <p>
 * If the input images were to represent the segmentation (labelled) masks,
 * the output image can be then understood as a voting map of how likely every
 * voxel should be part of the segmentation result. Every input image has its
 * associated weight for this voting, which also a parameter to this class.
 * One can then threshold the output and obtain consensus binary segmentation mask.
 *
 * Inputs: Collection of labelled masks + weights, TRA mask (3rd param: threshold parameter)
 * Output: Combined image
 *
 * @author Vladimír Ulman
 * @author Cem Emre Akbas
 */
//PUTBACK// @Plugin(type = Ops.Images.CombineGTs.class)
public class DefaultCombineGTsViaMarkers<T extends RealType<T>>
//PUTBACK//	extends AbstractBinaryComputerOp<Collection<RandomAccessibleInterval<T>>,
//PUTBACK//		Img<UnsignedShortType>, Img<UnsignedShortType>>
//PUTBACK//	implements Ops.Images.CombineGTs
{
	public DefaultCombineGTsViaMarkers(final OpService _ops) {
		ops = _ops;
	}

	@Parameter
	private Vector<Float> inWeights;

	@Parameter
	private float threshold;

	@Parameter
	private String dbgImgFileName;

	@Parameter
	private T type;

	@Parameter
	private OpService ops;

	/// Flag the "operational mode" regarding labels touching image boundary
	private final Boolean removeMarkersAtBoundary = false;
	/**
	 * Remove the whole colliding marker if the volume of its colliding portion
	 * is larger than this value. Set to zero (0) if even a single colliding
	 * voxel shall trigger removal of the whole marker.
	 */
	private final float removeMarkersCollisionThreshold = 0.1f;

	/**
	 * Flag if original TRA labels should be used for labels for which collision
	 * was detected and the merging process was not able to recover them, or the
	 * marker was not discovered at all.
	 */
	private final Boolean insertTRAforCollidingOrMissingMarkers = false;

	///sets explicitly the parameters that SciJava normally supplies in its own way...
	public void setParams(final Vector<Float> _inWeights,
	                      final float _threshold,
	                      final String _dbgImgFileName)
	{
		inWeights = _inWeights;
		threshold = _threshold;
		dbgImgFileName = _dbgImgFileName;
	}

//PUTBACK// 	@Override
	public
	void compute(final Collection<RandomAccessibleInterval<T>> inImgs,
	             final Img<UnsignedShortType> markerImg,
	             final Img<UnsignedShortType> outImg)
	{
		System.out.println("Default CombineGTsViaMarkers plug-in version");

		if (inImgs.size() != inWeights.size())
			throw new RuntimeException("Arrays with input images and weights are of different lengths.");

		//da plan:
		//iterate over all voxels of the input marker image and look for not
		//yet found marker, and for every such new discovered, do:
		//from all input images extract all labelled components that intersect
		//with the marker in more than half of the total marker voxels, combine
		//these components and threshold according to the given input threshold
		//(the 3rd param), save this thresholded component under the discovered marker
		//
		//while saving the marker, it might overlap with some other already
		//saved marker; mark such voxels specifically in the output image for
		//later post-processing

		//some constants to be used:
		//short-cut for increasing voxel values when combining multiple masks
		final FloatType ONE = new FloatType(1);

		//after merging, only voxels above this value are used to form the final mask
		final float THRESHOLD = threshold;

		//label for the voxels in the "collision area" of more labels
		final int INTERSECTION = (int)outImg.firstElement().getMaxValue();


		//(bounded) accessing cursors to read/write values from images:
		//first, determine maximal sweeping/accessing interval
		final long[] minBound = new long[markerImg.numDimensions()];
		final long[] maxBound = new long[markerImg.numDimensions()];
		markerImg.min(minBound);
		markerImg.max(maxBound);
		final FinalInterval mInterval = new FinalInterval(minBound, maxBound);

		//second, setup the (optimal) sweep iterators for the input images
		final Vector<RandomAccess<T>> inCursors = new Vector<>(inImgs.size());
		for (Iterator<RandomAccessibleInterval<T>> i = inImgs.iterator(); i.hasNext();  )
			inCursors.add(i.next().randomAccess(mInterval));

		//third, create a temporary image...
		markerImg.dimensions(maxBound);
		final Img<FloatType> tmpImg
			= markerImg.factory().imgFactory(new FloatType()).create(maxBound);

		//...and prepare its cursor
		final RandomAccess<FloatType> tmpCursor = tmpImg.randomAccess(mInterval);

		//finally, init the output image
		final Cursor<UnsignedShortType> outCursor = outImg.localizingCursor();
		while (outCursor.hasNext())
			outCursor.next().setZero();


		//cursor to sweep over the marker image, and its "position vector"
		final Cursor<UnsignedShortType> mCursor = markerImg.localizingCursor();
		pos = new int[markerImg.numDimensions()];

		//set to remember already discovered TRA markers
		//(with initial capacity set for 100 markers)
		HashSet<Integer> mDiscovered = new HashSet<>(100);
		//of these, the following markers were processed but will be removed...
		HashSet<Integer> mColliding = new HashSet<>(100);
		HashSet<Integer> mBordering = new HashSet<>(100);
		//of these, no counterparts were found for these markers
		HashSet<Integer> mNoMatches = new HashSet<>(100);

		//markers with which the current one is in collision
		HashSet<Integer> localColliders = new HashSet<>(100);

		//number of colliding and non-colliding voxels per marker
		//NB: used to determine portion of the colliding volume
		HashMap<Integer,Long> mCollidingVolume = new HashMap<>(100);
		HashMap<Integer,Long> mNoCollidingVolume = new HashMap<>(100);

		//sweep over the marker image
		while (mCursor.hasNext())
		{
			mCursor.next();
			final int curMarker = mCursor.get().getInteger();

			//scan for not yet observed markers (and ignore background values...)
			if ( curMarker > 0 && (!mDiscovered.contains(curMarker)) )
			{
				//found a new marker, determine its size and the AABB it spans
				final long markerSizeRef = findAABB(mCursor, minBound,maxBound);
/*
				//report detected markers just for debug
				System.out.print("marker "+mCursor.get().getInteger()+": lower corner: (");
				for (int d=0; d < minBound.length-1; ++d)
					System.out.print(minBound[d]+",");
				System.out.println(minBound[minBound.length-1]+")");
				System.out.print("marker "+mCursor.get().getInteger()+": upper corner: (");
				for (int d=0; d < maxBound.length-1; ++d)
					System.out.print(maxBound[d]+",");
				System.out.println(maxBound[maxBound.length-1]+"), size="+markerSizeRef);
*/
				//the sweeping interval just around this marker
				final Cursor<UnsignedShortType> mSubCursor
					= Views.interval(markerImg, minBound,maxBound).localizingCursor();

				//sweep over all input images
				int noOfMatchingImages = 0;
				for (int i = 0; i < inCursors.size(); ++i)
				{
					//short-cut
					final RandomAccess<T> inCursor = inCursors.get(i);

					//find the corresponding label in the input image
					final float matchingLabel = findMatchingLabel(inCursor,
						mSubCursor, curMarker, markerSizeRef);

					//System.out.println(i+". image: found label "+matchingLabel);

					if (matchingLabel > 0)
					{
						//extract this label into a temporary image
						//(from which we will threshold it and insert into the output image)
						//
						//change the "adding constant" to the weight of this image
						ONE.set(inWeights.get(i));

						//sweep the _entire_ input image and "copy" the bestLabel to the tmp image
						//NB: output img drives sweeping (as it is the target to be filled)
						outCursor.reset();
						while (outCursor.hasNext())
						{
							outCursor.next();
							outCursor.localize(pos);

							//initiate the tmp image if we are processing the first input image 
							tmpCursor.setPosition(pos);
							if (noOfMatchingImages == 0) tmpCursor.get().setZero();

							inCursor.setPosition(pos);
							if (inCursor.get().getRealFloat() == matchingLabel)
							{
								//found the label, "copy" it
								tmpCursor.get().add(ONE);
							}
						}

						//increase the counter...
						++noOfMatchingImages;
					}
				}
/*
				//save the debug image
				try {
					ImgSaver imgSaver = new ImgSaver();
					imgSaver.saveImg("/Users/ulman/DATA/dbgMerge__"+curMarker+".tif", tmpImg);
				}
				catch (UnsupportedOperationException | ImgIOException | IncompatibleTypeException e) {
					System.out.println("Unable to write output file.");
				}
				//....end save....
*/
				//flags to see if we are facing any specifig problem
				//during the upcomming thresholding and merging
				Boolean inCollision = false;
				Boolean atBorder = false;
				Boolean foundAtAll = false;
				localColliders.clear();

				//init the volume aggregators
				mCollidingVolume.put(curMarker,0L);
				mNoCollidingVolume.put(curMarker,0L);

				//now, threshold the tmp image (provided we have written there something
				//at all) and store it with the appropriate label in the output image
				outCursor.reset();
				while (outCursor.hasNext() && noOfMatchingImages > 0)
				{
					outCursor.next();
					outCursor.localize(pos);

					tmpCursor.setPosition(pos);
					if (tmpCursor.get().get() >= THRESHOLD)
					{
						//voxel to be inserted into the output final label mask
						foundAtAll = true;

						final int otherMarker = outCursor.get().getInteger();
						if (otherMarker == 0)
						{
							//inserting into an unoccupied voxel
							outCursor.get().set(curMarker);
							mNoCollidingVolume.put(curMarker,mNoCollidingVolume.get(curMarker)+1);
						}
						else
						{
							//collision detected
							outCursor.get().set(INTERSECTION);
							mCollidingVolume.put(curMarker,mCollidingVolume.get(curMarker)+1);
							inCollision = true;

							if (otherMarker != INTERSECTION)
							{
								localColliders.add(otherMarker);

								//update also stats of the other guy
								//because he was not intersecting here previously
								mNoCollidingVolume.put(otherMarker,mNoCollidingVolume.get(otherMarker)-1);
								mCollidingVolume.put(otherMarker,mCollidingVolume.get(otherMarker)+1);
							}
						}

						//check if we are at the image boundary
						for (int i = 0; i < pos.length && !atBorder; ++i)
							if (pos[i] == mInterval.min(i) || pos[i] == mInterval.max(i))
								atBorder = true;
					}
				}

				//finally, mark we have processed this marker
				mDiscovered.add(curMarker);

				//acknowledge the main flag:
				atBorder &= removeMarkersAtBoundary;

				//some per marker report:
				System.out.print("TRA marker: "+curMarker+" , images matching: "+noOfMatchingImages);

				//outcomes in 4 states:
				//TRA marker was secured (TODO: secured after threshold increase)
				//TRA marker was hit but removed due to collision, or due to border
				//TRA marker was not hit at all

				//also note the outcome of this processing, which is exclusively:
				//found, not found, in collision, at border
				if (!foundAtAll)
				{
					mNoMatches.add(curMarker);
					System.out.println(" , not included because not matched in results");
				}
				else
				{
					if (atBorder)
					{
						mBordering.add(curMarker);
						System.out.println(" , detected to be at boundary");
					}
					else if (inCollision)
						//NB: mColliding.add() must be done after all markers are processed
						System.out.println(" , detected to be in collision");
					else
						System.out.println(" , secured for now");
				}

				if (localColliders.size() > 0)
				{
					System.out.print("guys colliding with this marker: ");
					for (Iterator<Integer> it = localColliders.iterator(); it.hasNext(); )
						System.out.print(it.next()+",");
					System.out.println();
				}
			} //after marker processing
		} //after all voxel looping

		//save now a debug image
		try {
			if (dbgImgFileName != null && dbgImgFileName.length() > 0)
			{
				ImgSaver imgSaver = new ImgSaver();
				imgSaver.saveImg(dbgImgFileName, outImg);
			}
		}
		catch (UnsupportedOperationException | ImgIOException | IncompatibleTypeException e) {
			System.out.println("Unable to write debug output file.");
		}

		//check colliding markers and decide if to be removed or not
		//and fill a histogram array at the same time
		final int[] collHistogram = new int[11];
		for (Iterator<Integer> it = mCollidingVolume.keySet().iterator(); it.hasNext(); )
		{
			final int marker = it.next();

			//get proportion of colliding volume from the whole marker volume
			float collRatio = (float)mCollidingVolume.get(marker);
			collRatio /= (float)(mNoCollidingVolume.get(marker)+mCollidingVolume.get(marker));

			//decide if to mark the marker for removal
			if ( (collRatio > removeMarkersCollisionThreshold)
			  && (!mBordering.contains(marker)) ) mColliding.add(marker);
			  //NB: should not be in two classes simultaneously

			//update the histogram
			if (!mNoMatches.contains(marker))
				collHistogram[(int)(collRatio*10.f)]++;
		}

		//jobs: remove border-touching cells
		//jobs: remove colliding cells
		//sweep the output image and do the jobs
		outCursor.reset();
		while (outCursor.hasNext())
		{
			final int label = outCursor.next().getInteger();
			if (label == INTERSECTION)
			{
				outCursor.get().setZero();
				//System.out.println("cleaning: collision intersection");
			}
			else if (mColliding.contains(label))
			{
				outCursor.get().setZero();
				//System.out.println("cleaning: rest of a colliding marker");
			}
			else if (mBordering.contains(label))
			{
				outCursor.get().setZero();
				//System.out.println("cleaning: marker at boundary");
			}
		}

		removeIsolatedIslands(outImg);

		//report details of colliding markers:
		System.out.println("reporting colliding markers:");
		for (Iterator<Integer> it = mCollidingVolume.keySet().iterator(); it.hasNext(); )
		{
			final int marker = it.next();
			float collRatio = (float)mCollidingVolume.get(marker);
			collRatio /= (float)(mNoCollidingVolume.get(marker)+mCollidingVolume.get(marker));
			if (collRatio > 0.f)
				System.out.println("marker: "+marker+": colliding "+mCollidingVolume.get(marker)
				                  +" and non-colliding "+mNoCollidingVolume.get(marker)
				                  +" voxels ( "+collRatio+" ) "
				                  +(collRatio > removeMarkersCollisionThreshold? "too much":"acceptable"));
		}

		//report the histogram of colliding volume ratios
		for (int hi=0; hi < 10; ++hi)
			System.out.println("HIST: "+(hi*10)+" %- "+(hi*10+9)+" % collision area happened "
			                  +collHistogram[hi]+" times");
		System.out.println("HIST: 100 %- 100 % collision area happened "
		                  +collHistogram[10]+" times");

		//also some per image report:
		final int allMarkers = mDiscovered.size();
		final int okMarkers = allMarkers - mNoMatches.size() - mBordering.size() - mColliding.size();
		System.out.println("not found markers    = "+mNoMatches.size()
			+" = "+ 100.0f*(float)mNoMatches.size()/(float)allMarkers +" %");
		System.out.println("markers at boundary  = "+mBordering.size()
			+" = "+ 100.0f*(float)mBordering.size()/(float)allMarkers +" %");
		System.out.println("markers in collision = "+mColliding.size()
			+" = "+ 100.0f*(float)mColliding.size()/(float)allMarkers +" %");
		System.out.println("secured markers      = "+okMarkers
			+" = "+ 100.0f*(float)okMarkers/(float)allMarkers +" %");

		if (insertTRAforCollidingOrMissingMarkers && (mColliding.size() > 0 || mNoMatches.size() > 0))
		{
			//sweep the output image and add missing TRA markers
			final RandomAccess<UnsignedShortType> tCursor
				= markerImg.randomAccess(mInterval);

			//TODO: accumulate numbers of how many times submitting of TRA label
			//would overwrite existing label in the output image, and report it
			outCursor.reset();
			while (outCursor.hasNext())
			{
				final int outLabel = outCursor.next().getInteger();

				outCursor.localize(pos);
				tCursor.setPosition(pos);

				final int traLabel = tCursor.get().getInteger();
				if (outLabel == 0 && (mColliding.contains(traLabel) || mNoMatches.contains(traLabel)))
					outCursor.get().set(traLabel);
			}
		}
	}
	
	
	/**
	 * Determines the minimal AABB (axes-aligned bounding box) around the marker
	 * found at the cursor position. It is assumed that the cursor points on the
	 * first occurence of such marker.
	 * 
	 * @param mCursor	Position of the first occurence of the marker (will not be changed)
	 * @param minBound	output "lower-left" corner of the box (must be preallocated)
	 * @param maxBound	output "upper-right" corner of the box (must be preallocated)
	 * @return number of voxels occupied by this marker
	 */
	private
	long findAABB(final Cursor<UnsignedShortType> mCursor,
	              final long[] minBound, final long[] maxBound)
	{
		//time-saver: the marker we search for, and the current position array
		final int marker = mCursor.get().getInteger();
		final long[] pos = new long[minBound.length];

		//init the output variables
		long size = 0; //nothing seen yet since we reset() below
		mCursor.localize(minBound); //BB spans this one pixel yet
		mCursor.localize(maxBound);

		//working copy of the input cursor
		final Cursor<UnsignedShortType> cursor = mCursor.copyCursor();
		cursor.reset();
		//NB: for some reason we cannot continue from the mCursor's place, in which case
		//the cursor does not see the original image content (until we cursor.reset() it)

		//scan the rest of the image
		while (cursor.hasNext())
		{
			//the same marker?
			if (cursor.next().getInteger() == marker)
			{
				//yes, update the box corners
				cursor.localize(pos);
				for (int d = 0; d < minBound.length; ++d)
				{
					if (pos[d] < minBound[d]) minBound[d] = pos[d];
					if (pos[d] > maxBound[d]) maxBound[d] = pos[d];
				}
				++size;
			}
		}
		
		return size;
	}


	/**
	 * Sweeps over \e inMarker labelled voxels inside the bounding box
	 * (represented with \e mSubCursor) of the marker image, checks labels
	 * found in the corresponding voxels in the input image (accessible via
	 * \e inCursor), and returns the most frequently occuring such label
	 * (provided also it occurs more than half of the marker size \e inMarkerSize).
	 * The functions returns -1 if no such label is found.
	 *
	 * @param inCursor		Accessor to the input image (whose label is to be returned)
	 * @param mSubCursor	Sweeper of the input marker image
	 * @param inMarker		Marker (from the input marker image) in question...
	 * @param inMarkerSize	...and number of voxels it spans over
	 */
	private
	float findMatchingLabel(final RandomAccess<T> inCursor,
	                        final Cursor<UnsignedShortType> mSubCursor,
	                        final int inMarker,
	                        final long inMarkerSize)
	{
		//keep frequencies of labels discovered across the marker volume
		HashMap<Float,Integer> labelCounter = new HashMap<Float,Integer>();
		Integer count = null;

		//find relevant label(s), if any
		Cursor<UnsignedShortType> c = mSubCursor.copyCursor();
		while (c.hasNext())
		{
			//are we over the original marker in the marker image?
			if (c.next().getInteger() == inMarker)
			{
				//yes, we are... check what value is in the input image
				c.localize(pos);
				inCursor.setPosition(pos);

				//update the counter of found values
				count = labelCounter.get(inCursor.get().getRealFloat()); 
				labelCounter.put(inCursor.get().getRealFloat(), count == null ? 1 : count+1);
			}
		}

		//now, find the most frequent input label...
		//(except for the background...)
		float bestLabel = -1;
		count = 0;
		for (Iterator<Float> keys = labelCounter.keySet().iterator(); keys.hasNext(); )
		{
			float curLabel = keys.next();
			if (labelCounter.get(curLabel) > count && curLabel > 0)
			{
				bestLabel = curLabel;
				count = labelCounter.get(curLabel);
			}
		}

		//check if the most frequent one also spans at least half
		//of the input marker volume
		return ( (2*count > inMarkerSize)? bestLabel : -1);
	}
	
	//temporary buffer for position handling, shared between functions overhere
	//(in a believe that we avoid many allocs)
	private int pos[];

	private <O extends IntegerType<O>> void removeIsolatedIslands(Img<O> inImg)
	{
		//since there are no overlaps in the input labels, ImgLabeling.mapping will
		//be of the same length as there are different labels in the input image,
		//that said, there will be no more of them than what the input voxel type can store,
		//that said, the voxel type of the backing image can be used the same
		ImgLabeling<Integer, O> lImg = new ImgLabeling<>(inImg.factory().create(inImg));

		//this "translates" discovered labels into the lImg -- the labeling map
		Cursor<LabelingType<Integer>> out = lImg.cursor();
		RandomAccess<O> in = inImg.randomAccess();
		while( out.hasNext() )
		{
			LabelingType<Integer> labels = out.next();
			in.setPosition(out);
			labels.add( in.get().getInteger() );
		}

		final int noComponents = lImg.getMapping().numSets()-2;		// get the number of detected components
		int total_removed = 0;    // stores the number of removed components from the input image
		int max_removed = 0;      // stores the maximum number of removed components from one particular label region
		int num_removed_img = 0;  // stores the number of manipulated label regions

		LabelRegions<Integer> regions = new LabelRegions<>(lImg);  // get regions from the labeled image

		Img<O> outputImg = inImg.factory().create(inImg);		// create the empty output image, its size is the same as the input image
		// now loop through all the regions in the labeling of input image
		for (LabelRegion<Integer> region : regions) {
			Img<O> singlelabelImg = copyRegion(inImg, region);   // copy the current label region to a blank image
			ImgLabeling<Integer,O> singlelImg
			  = ops.labeling().cca((RandomAccessibleInterval<O>) singlelabelImg, ConnectedComponents.StructuringElement.EIGHT_CONNECTED);   // run cca over the image with only one label region

			LabelRegions<Integer> singleregions = new LabelRegions<>(singlelImg);	// get new labels from the sub-image. Isolated islands now have different labels.
			LabelRegion<Integer> biggest_subregion = null;		// create a blank region
			// find the biggest subregion of the particular label
			if (singleregions.getExistingLabels().size() > 0) {		// to skip the background which is also considered as a region.
				for (LabelRegion<Integer> singleregion : singleregions) {    // loop through all the subregions in one particular label region
					if ( biggest_subregion == null || singleregion.size() > biggest_subregion.size() ) {
						biggest_subregion = singleregion;		// determine and update the biggest subregion
					}
				}
				outputImg = addRegion(inImg, outputImg, biggest_subregion);   // collect biggest subregions in the output image
			}
			if (singleregions.getExistingLabels().size()>1) {	// This part is only executed for label regions that has at least 2 isolated components
				num_removed_img = num_removed_img + 1;
				int num_removed_comp = singleregions.getExistingLabels().size()-1;	// all subregions except the biggest one is deleted
				total_removed = total_removed + num_removed_comp;
				if (num_removed_comp > max_removed) {
					max_removed = num_removed_comp;
				}
			}
		}
		//uiService.show("Result", outputImg);
		System.out.println("Components removed in " + num_removed_img + "/" + noComponents + " regions. Max removed in a particular region is " + max_removed + ". In total, " + total_removed + " components removed.");

		//now, rewrite the original input with its cleaned (no isolated islands) version, that is, with the outputImg
		LoopBuilder.setImages(inImg, outputImg).forEachPixel( (i, o) -> i.setInteger(o.getInteger()) );
	}

	private <O extends IntegerType<O>> Img<O> copyRegion(Img<O> input, LabelRegion<Integer> region) {
		// This method copies out given region from the input image to a blank image. The returned subimage will be the input of the cca.
		LabelRegionCursor singlecc =  region.localizingCursor();
		RandomAccess<O> ra = input.randomAccess();
		Img<O> singlelabelImg = input.factory().create(input);		// create an empty image
		RandomAccess<O> singlerr = singlelabelImg.randomAccess();
		while (singlecc.hasNext())		// iterate cursor over the given region
		{
			singlecc.next();
			singlerr.setPosition(singlecc);  // set cursor position on the component
			ra.setPosition(singlecc);		// access position that holds the desired pixel value
			singlerr.get().setInteger(ra.get().getInteger());	// get the pixel value from the input image and copy it to the subimage 
		}
		return singlelabelImg;
	}

	private <O extends IntegerType<O>> Img<O> addRegion(Img<O> input, Img<O> collectedlabelImg, LabelRegion<Integer> region) {
		// This method copies the given region from input image to the output image that collects biggest subregions
		LabelRegionCursor singlecc =  region.localizingCursor();
		RandomAccess<O> ra = input.randomAccess();
		RandomAccess<O> singlerr = collectedlabelImg.randomAccess();
		while (singlecc.hasNext())    // iterate cursor over the given region
		{
			singlecc.next();
			singlerr.setPosition(singlecc);		// set cursor position on the component
			ra.setPosition(singlecc);			// access position that holds the desired pixel value
			singlerr.get().setInteger(ra.get().getInteger());	// get the pixel value from the input image and add it to the given collector image.
		}
		return collectedlabelImg;
	}
}
