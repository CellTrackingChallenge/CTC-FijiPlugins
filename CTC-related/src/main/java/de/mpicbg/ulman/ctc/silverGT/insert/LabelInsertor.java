package de.mpicbg.ulman.ctc.silverGT.insert;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import java.util.HashSet;

/**
 * Inserts the labels (into image of voxel types LT -- Label Type) from (possibly a working)
 * image (of voxel types ET -- Extracted_as Type) with individual fused segment.
 *
 * @param <LT>  voxel type of the marker/label image (on which the labels are synchronized)
 * @param <ET>  voxel type of the output image (into which the input labels are extracted)
 */
public interface LabelInsertor<LT extends IntegerType<LT>, ET extends RealType<ET>>
{
	class InsertionStatus
	{
		/** was the marker found in the source image at all? */
		public boolean foundAtAll;

		/** is the marker touching image boundary? */
		public boolean atBorder;

		/** is the marker overlapping/colliding with some other marker in the output image? */
		public boolean inCollision;

		/** list of other markers in the output image that would overlap with this one */
		public HashSet<Integer> localColliders = new HashSet<>(100);

		/** volume of the marker that was easily inserted, i.e. with out any collision/overlap */
		public long collidingVolume;
		/** volume of the marker that is in collision/overlap with some other marker */
		public long notCollidingVolume;

		public
		void clear()
		{
			foundAtAll = false;
			atBorder = false;
			inCollision = false;
			localColliders.clear();
			collidingVolume = 0;
			notCollidingVolume = 0;
		}
	}

	/**
	 * It is assumed that any non-zero pixel from the 'inSingleLabelImg'
	 * will be inserted into the 'outResultImg' with a value of 'outMarker'.
	 * The information regarding the insertion is stored in the 'operationStatus',
	 * if caller supplies it (yes, that parameter may be set to null).
	 */
	void insertLabel(final Img<ET> inSingleLabelImg,
	                 final Img<LT> outResultImg,
	                 final int outMarker,
	                 final InsertionStatus operationStatus);
}
