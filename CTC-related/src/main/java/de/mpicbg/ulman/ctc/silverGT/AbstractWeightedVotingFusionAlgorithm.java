package de.mpicbg.ulman.ctc.silverGT;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.loops.LoopBuilder;
import org.scijava.log.LogService;

import net.imglib2.*;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.view.Views;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

import io.scif.img.ImgSaver;
import io.scif.img.ImgIOException;

import java.util.Iterator;
import java.util.Vector;
import java.util.HashSet;
import net.imglib2.type.numeric.IntegerType;

import de.mpicbg.ulman.ctc.silverGT.extract.LabelExtractor;
import de.mpicbg.ulman.ctc.silverGT.extract.MajorityOverlapBasedLabelExtractor;
import de.mpicbg.ulman.ctc.silverGT.fuse.LabelFuser;
import de.mpicbg.ulman.ctc.silverGT.insert.LabelInsertor;
import de.mpicbg.ulman.ctc.silverGT.insert.CollisionsAwareLabelInsertor;
import de.mpicbg.ulman.ctc.silverGT.postprocess.LabelPostprocessor;

/**
 * Skeleton that iterates over the individual markers from the marker image,
 * extracts the marker and collects incident segmentation masks (labels) from
 * the input images (using some method from the 'extract' folder), fuses them
 * collected labels (using some method from the 'fuse' folder), and inserts
 * the fused (created) segment (using some method from the 'insert' folder),
 * and finally cleans up the results after all of them are inserted (using some
 * method from the 'postprocess' folder).
 */
public abstract
class AbstractWeightedVotingFusionAlgorithm<IT extends RealType<IT>, LT extends IntegerType<LT>>
implements WeightedVotingFusionAlgorithm<IT,LT>
{
	///prevent from creating the class without any connection
	@SuppressWarnings("unused")
	private
	AbstractWeightedVotingFusionAlgorithm()
	{ log = null; } //this is to get rid of some warnings

	protected final LogService log;

	public
	AbstractWeightedVotingFusionAlgorithm(final LogService _log)
	{
		if (_log == null)
			throw new RuntimeException("Please, give me existing LogService.");

		log = _log;

		//setup the required components
		setFusionComponents();

		//inevitable sanity test to see if the user has
		//implemented the setFusionComponents() correctly
		testFusionComponents();
	}


	private
	void testFusionComponents()
	{
		if (labelExtractor == null)
			throw new RuntimeException("this.labelExtractor must be set");
		/*
		if (labelExtractor instanceof LabelExtractor)
			throw new RuntimeException("this.labelExtractor must implement LabelExtractor");
		*/

		if (labelFuser == null)
			throw new RuntimeException("this.labelFuser must be set");

		if (labelInsertor == null)
			throw new RuntimeException("this.labelInsertor must be set");

		if (labelCleaner == null)
			throw new RuntimeException("this.labelCleaner must be set");
	}

	/** Any class that extends this one must implement this method.
	    The purpose of this method is to define this.labelExtractor,
	    this.labelFuser, this.labelInsertor and this.labelCleaner. */
	protected abstract
	void setFusionComponents();

	//setup extract, fuse, insert, postprocess (clean up)
	LabelExtractor<IT,LT,DoubleType> labelExtractor = null;
	LabelFuser<IT,DoubleType> labelFuser = null;
	CollisionsAwareLabelInsertor<LT,DoubleType> labelInsertor = null;
	LabelPostprocessor<LT> labelCleaner = null;


	protected Vector<Double> inWeights;
	protected double threshold;

	@Override
	public
	void setWeights(final Vector<Double> weights)
	{
		inWeights = weights; //TODO: should make own copy? no!
	}

	@Override
	public
	void setThreshold(final double minSumOfWeights)
	{
		threshold = minSumOfWeights;
	}



	/// Flag the "operational mode" regarding labels touching image boundary
	public boolean removeMarkersAtBoundary = false;

	/**
	 * Remove the whole colliding marker if the volume of its colliding portion
	 * is larger than this value. Set to zero (0) if even a single colliding
	 * voxel shall trigger removal of the whole marker.
	 */
	public float removeMarkersCollisionThreshold = 0.1f;

	/**
	 * Flag if original TRA labels should be used for labels for which collision
	 * was detected and the merging process was not able to recover them, or the
	 * marker was not discovered at all.
	 */
	public Boolean insertTRAforCollidingOrMissingMarkers = false;

	public String dbgImgFileName;

	@Override
	public
	Img<LT> fuse(final Vector<RandomAccessibleInterval<IT>> inImgs,
	             final Img<LT> markerImg)
	{
		if (inImgs.size() != inWeights.size())
			throw new RuntimeException("Arrays with input images and weights are of different lengths.");

		if (labelExtractor == null || labelFuser == null || labelInsertor == null || labelCleaner == null)
			throw new RuntimeException("Object is not fully and properly initialized.");

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

		//create a temporary image (of the same iteration order as the markerImg)
		final Img<DoubleType> tmpImg
			= markerImg.factory().imgFactory(new DoubleType()).create(markerImg);

		//create the output image (of the same iteration order as the markerImg),
		//and init it
		final Img<LT> outImg = markerImg.factory().create(markerImg);
		LoopBuilder.setImages(outImg).forEachPixel( (a) -> a.setZero() );

		//aux params for the fusion
		final Vector<RandomAccessibleInterval<IT>> selectedInImgs  = new Vector<>(inWeights.size());
		final Vector<Float>                       selectedInLabels = new Vector<>(inWeights.size());

		//set to remember already discovered TRA markers
		//(with initial capacity set for 100 markers)
		HashSet<Integer> mDiscovered = new HashSet<>(100);

		//init insertion (includes to create (re-usable) insertion status object)
		final LabelInsertor.InsertionStatus insStatus = new LabelInsertor.InsertionStatus();
		labelInsertor.initialize(outImg);

		//also prepare the positions holding aux array, and bbox corners
		final long[] minBound = new long[markerImg.numDimensions()];
		final long[] maxBound = new long[markerImg.numDimensions()];

		//sweep over the marker image
		final Cursor<LT> mCursor = markerImg.localizingCursor();
		while (mCursor.hasNext())
		{
			final int curMarker = mCursor.next().getInteger();

			//scan for not yet observed markers (and ignore background values...)
			if ( curMarker > 0 && (!mDiscovered.contains(curMarker)) )
			{
				//found a new marker, determine its size and the AABB it spans
				MajorityOverlapBasedLabelExtractor.findAABB(mCursor, minBound,maxBound);
/*
				//report detected markers just for debug
				System.out.print("marker "+mCursor.get().getInteger()+": lower corner: (");
				for (int d=0; d < minBound.length-1; ++d)
					System.out.print(minBound[d]+",");
				System.out.println(minBound[minBound.length-1]+")");
				System.out.print("marker "+mCursor.get().getInteger()+": upper corner: (");
				for (int d=0; d < maxBound.length-1; ++d)
					System.out.print(maxBound[d]+",");
				System.out.println(maxBound[maxBound.length-1]+")");
*/

				//sweep over all input images
				selectedInImgs.clear();
				selectedInLabels.clear();
				int noOfMatchingImages = 0;
				for (int i = 0; i < inImgs.size(); ++i)
				{
					//find the corresponding label in the input image (in the restricted interval)
					final float matchingLabel = labelExtractor.findMatchingLabel(
							Views.interval(inImgs.get(i), minBound,maxBound),
							Views.interval(markerImg,     minBound,maxBound),
							curMarker);
					//System.out.println(i+". image: found label "+matchingLabel);

					if (matchingLabel > 0)
					{
						selectedInImgs.add(inImgs.get(i));
						selectedInLabels.add(matchingLabel);
						++noOfMatchingImages;
					}
					else
					{
						selectedInImgs.add(null);
						selectedInLabels.add(0.f);
					}
				}

				if (noOfMatchingImages > 0)
				{
					//reset the temporary image beforehand
					LoopBuilder.setImages(tmpImg).forEachPixel( (a) -> a.setZero() );

					//fuse the selected labels into it
					labelFuser.fuseMatchingLabels(selectedInImgs,selectedInLabels,
					                              labelExtractor,inWeights, tmpImg);
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
					//insert the fused segment into the output image
					labelInsertor.insertLabel(tmpImg, outImg,curMarker, insStatus);
				}
				else
				{
					insStatus.clear();
					labelInsertor.mCollidingVolume.put(curMarker,0L);
					labelInsertor.mNoCollidingVolume.put(curMarker,0L);
				}

				//some per marker report:
				System.out.print("TRA marker: "+curMarker+" , images matching: "+noOfMatchingImages);

				//outcomes in 4 states:
				//TRA marker was secured (TODO: secured after threshold increase)
				//TRA marker was hit but removed due to collision, or due to border
				//TRA marker was not hit at all

				//also note the outcome of this processing, which is exclusively:
				//found, not found, in collision, at border
				if (!insStatus.foundAtAll)
				{
					labelInsertor.mNoMatches.add(curMarker);
					System.out.println(" , not included because not matched in results");
				}
				else
				{
					if (removeMarkersAtBoundary & insStatus.atBorder)
					{
						labelInsertor.mBordering.add(curMarker);
						System.out.println(" , detected to be at boundary");
					}
					else if (insStatus.inCollision)
						//NB: labelInsertor.mColliding.add() must be done after all markers are processed
						System.out.println(" , detected to be in collision");
					else
						System.out.println(" , secured for now");
				}

				if (insStatus.localColliders.size() > 0)
				{
					System.out.print("guys colliding with this marker: ");
					for (Iterator<Integer> it = insStatus.localColliders.iterator(); it.hasNext(); )
						System.out.print(it.next()+",");
					System.out.println();
				}

				//finally, mark we have processed this marker
				mDiscovered.add(curMarker);
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

		final int allMarkers = mDiscovered.size();
		final int[] collHistogram
			= labelInsertor.finalize(outImg,removeMarkersCollisionThreshold,removeMarkersAtBoundary);

		// --------- CCA analyses ---------
		mDiscovered.clear();
		final Cursor<LT> outFICursor = outImg.cursor();
		while (outFICursor.hasNext())
		{
			final int curMarker = outFICursor.next().getInteger();

			//scan for not yet observed markers (and ignore background values...)
			if ( curMarker > 0 && (!mDiscovered.contains(curMarker)) )
			{
				labelCleaner.processLabel(outImg, curMarker);

				//and mark we have processed this marker
				mDiscovered.add(curMarker);
			}
		}
		// --------- CCA analyses ---------

		//report details of colliding markers:
		System.out.println("reporting colliding markers:");
		for (final int marker : labelInsertor.mCollidingVolume.keySet())
		{
			float collRatio = (float) labelInsertor.mCollidingVolume.get(marker);
			collRatio /= (float) (labelInsertor.mNoCollidingVolume.get(marker) + labelInsertor.mCollidingVolume.get(marker));
			if (collRatio > 0.f)
				System.out.println("marker: " + marker + ": colliding " + labelInsertor.mCollidingVolume.get(marker)
						+ " and non-colliding " + labelInsertor.mNoCollidingVolume.get(marker)
						+ " voxels ( " + collRatio + " ) "
						+ (collRatio > removeMarkersCollisionThreshold ? "too much" : "acceptable"));
		}

		//report the histogram of colliding volume ratios
		for (int hi=0; hi < 10; ++hi)
			System.out.println("HIST: "+(hi*10)+" %- "+(hi*10+9)+" % collision area happened "
			                  +collHistogram[hi]+" times");
		System.out.println("HIST: 100 %- 100 % collision area happened "
		                  +collHistogram[10]+" times");

		//also some per image report:
		final int okMarkers = allMarkers - labelInsertor.mNoMatches.size() - labelInsertor.mBordering.size() - labelInsertor.mColliding.size();
		System.out.println("not found markers    = "+labelInsertor.mNoMatches.size()
			+" = "+ 100.0f*(float)labelInsertor.mNoMatches.size()/(float)allMarkers +" %");
		System.out.println("markers at boundary  = "+labelInsertor.mBordering.size()
			+" = "+ 100.0f*(float)labelInsertor.mBordering.size()/(float)allMarkers +" %");
		System.out.println("markers in collision = "+labelInsertor.mColliding.size()
			+" = "+ 100.0f*(float)labelInsertor.mColliding.size()/(float)allMarkers +" %");
		System.out.println("secured markers      = "+okMarkers
			+" = "+ 100.0f*(float)okMarkers/(float)allMarkers +" %");

		if (insertTRAforCollidingOrMissingMarkers && (labelInsertor.mColliding.size() > 0 || labelInsertor.mNoMatches.size() > 0))
		{
			//sweep the output image and add missing TRA markers
			//
			//TODO: accumulate numbers of how many times submitting of TRA label
			//would overwrite existing label in the output image, and report it
			LoopBuilder.setImages(outImg,markerImg).forEachPixel(
				(o,m) -> {
					final int outLabel = o.getInteger();
					final int traLabel = m.getInteger();
					if (outLabel == 0 && (labelInsertor.mColliding.contains(traLabel) || labelInsertor.mNoMatches.contains(traLabel)))
						outFICursor.get().setInteger(traLabel);
				} );
		}

		return outImg;
	}
}
