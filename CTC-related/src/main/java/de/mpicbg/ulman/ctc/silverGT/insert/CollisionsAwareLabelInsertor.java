package de.mpicbg.ulman.ctc.silverGT.insert;

import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.view.Views;
import net.imglib2.loops.LoopBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class CollisionsAwareLabelInsertor<LT extends IntegerType<LT>, ET extends RealType<ET>>
implements LabelInsertor<LT,ET>
{
	/** number of colliding voxels per marker,
	    NB: used to determine portion of the colliding volume */
	public HashMap<Integer,Long> mCollidingVolume = new HashMap<>(100);

	/** number of non-colliding voxels per marker,
	    NB: used to determine portion of the colliding volume */
	public HashMap<Integer,Long> mNoCollidingVolume = new HashMap<>(100);

	/** set of markers that are in some collision */
	public HashSet<Integer> mColliding = new HashSet<>(100);

	/** set of markers that are touching output image border */
	public HashSet<Integer> mBordering = new HashSet<>(100);

	/** set of markers for which not enough of input segments were found */
	public HashSet<Integer> mNoMatches = new HashSet<>(100);

	/** special label for the voxels in the "collision area" of more labels */
	private int INTERSECTION;


	public
	void initialize(final Img<LT> templateImg)
	{
		mCollidingVolume.clear();
		mNoCollidingVolume.clear();

		mColliding.clear();
		mBordering.clear();
		mNoMatches.clear();

		INTERSECTION = (int)(templateImg.firstElement().getMaxValue());
	}


	/** returns the collision size histogram */
	public
	int[] finalize(final Img<LT> outImg,
	               final float removeMarkersCollisionThreshold,
	               final boolean removeMarkersAtBoundary)
	{
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
		LoopBuilder.setImages(outImg).forEachPixel(
			(a) -> {
				final int label = a.getInteger();
				if (label == INTERSECTION)
				{
					a.setZero();
					//System.out.println("cleaning: collision intersection");

					//TODO: copy out the INTERSECTION voxels into a separate (initially empty) image;
					//      after this method is finished (i.e. after all too-much-intersecting labels
					//      are gone) iteratively "dilate" labels only within the area of the INTERSECTION
					//      voxels, which mimics a morphological watershed, but do not "dilate" boundary
					//      voxels; and keep iteraring until no change; copy back the "converted (into
					//      labels)" but originally INTERSECTION voxels
				}
				else if (mColliding.contains(label))
				{
					a.setZero();
					//System.out.println("cleaning: rest of a colliding marker");
				}
				else if (removeMarkersAtBoundary && mBordering.contains(label))
				{
					a.setZero();
					//System.out.println("cleaning: marker at boundary");
				}
			} );

		return collHistogram;
	}


	/** status-less wrapper around this.insertLabel() */
	public
	void insertLabel(final Img<ET> inSingleLabelImg,
	                 final Img<LT> outResultImg,
	                 final int outMarker)
	{
	    insertLabel(inSingleLabelImg,outResultImg,outMarker, fakeStatus);
	}
	private static InsertionStatus fakeStatus = new InsertionStatus();


	/**
	 * It is assumed that any non-zero pixel from the 'inSingleLabelImg'
	 * will be inserted into the 'outResultImg' with a value of 'outMarker'.
	 * The information regarding the insertion is stored in the 'operationStatus'.
	 */
	@Override
	public
	void insertLabel(final Img<ET> inSingleLabelImg,
	                 final Img<LT> outResultImg,
	                 final int outMarker,
	                 final InsertionStatus status)
	{
		status.clear();

		//now, threshold the tmp image (provided we have written there something
		//at all) and store it with the appropriate label in the output image
		final Cursor<ET> tmpFICursor = Views.flatIterable( inSingleLabelImg ).cursor();
		final Cursor<LT> outFICursor = Views.flatIterable( outResultImg ).localizingCursor();

		while (outFICursor.hasNext())
		{
			outFICursor.next();
			if (tmpFICursor.next().getRealFloat() > 0)
			{
				//voxel to be inserted into the output final label mask
				status.foundAtAll = true;

				final int otherMarker = outFICursor.get().getInteger();
				if (otherMarker == 0)
				{
					//inserting into an unoccupied voxel
					outFICursor.get().setInteger(outMarker);
					status.notCollidingVolume++;
				}
				else
				{
					//collision detected
					outFICursor.get().setInteger(INTERSECTION);
					status.collidingVolume++;
					status.inCollision = true;

					if (otherMarker != INTERSECTION)
					{
						status.localColliders.add(otherMarker);

						//update also stats of the other guy
						//because he was not intersecting here previously
						mNoCollidingVolume.put(otherMarker,mNoCollidingVolume.get(otherMarker)-1);
						mCollidingVolume.put(otherMarker,mCollidingVolume.get(otherMarker)+1);
					}
				}

				//check if we are at the image boundary
				for (int i = 0; i < outResultImg.numDimensions() && !status.atBorder; ++i)
					if ( outFICursor.getLongPosition(i) == outResultImg.min(i)
					  || outFICursor.getLongPosition(i) == outResultImg.max(i) ) status.atBorder = true;
			}
		}

		mCollidingVolume.put(  outMarker,status.collidingVolume);
		mNoCollidingVolume.put(outMarker,status.notCollidingVolume);
	}
}
