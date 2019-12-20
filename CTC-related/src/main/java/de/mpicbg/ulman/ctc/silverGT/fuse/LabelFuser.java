package de.mpicbg.ulman.ctc.silverGT.fuse;

import java.util.Vector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import de.mpicbg.ulman.ctc.silverGT.extract.LabelExtractor;

/**
 * Fuses selected labels from input images (of voxel type IT -- Input Type)
 * into some other image (of voxel type ET -- Extract_as Type).
 *
 * @param <IT>  voxel type of input images (with labels to be fused together)
 * @param <ET>  voxel type of the output image (into which the input labels are extracted)
 */
public interface LabelFuser<IT extends RealType<IT>, ET extends RealType<ET>>
{
	/**
	 * Fuses selected labels from input images into output image.
	 * The vector of input images may include null pointers.
	 * The values in the output image 'outImg' are not specified, except
	 * that non-zero values are understood to represent the fused segment.
	 *
	 * @param inImgs     image with labels
	 * @param inLabels   what label per image
	 * @param le         how to extract the label
	 * @param inWeights  what weight per image
	 * @param outImg     outcome of the fusion
	 */
	void fuseMatchingLabels(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                        final Vector<Float> inLabels,
	                        final LabelExtractor<IT,?,ET> le,
	                        final Vector<Double> inWeights,
	                        final Img<ET> outImg);
}
