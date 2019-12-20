package de.mpicbg.ulman.ctc.silverGT;

import net.imglib2.img.Img;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import java.util.Vector;

/**
 * The minimal interface to fuse together a collection of images showing instance
 * segmentations (with voxels of InputType - IT) where selection of instances is
 * driven by instance detection/segmentation image (with voxels of LabelType - LT).
 *
 * The input instance segmentation images may freely use any set of numbers for
 * their segmentation labels. The marker image should contain exactly one unique
 * label/marker for every object. The segments in the input images that shall
 * correspond to the same object are determined from the amount of overlap of these
 * segments with the relevant marker.
 *
 * The returned output image shall be a fusion of the input segments, and shall
 * carry the labels from the marker image.
 *
 * Implementing classes may use, and typically will be using, additional setter
 * methods to provide beforehand the parameters to the fusion process. Since some
 * of the parameters may relate to the input images, e.g. the weight of an image,
 * the collection holding references on the input images is a one that allows to
 * address images with indices.
 */
public
interface FusionAlgorithm <IT extends RealType<IT>, LT extends RealType<LT>>
{
	/** The workhorse method to fuse 'inImgs' synchronized over the 'markerImg'
	    with a default 'minFractionOfMarker' appropriate for the underlying algorithm.
	    Typically, the minimum requested overlap is 0.5. */
	Img<LT> fuse(final Vector<RandomAccessibleInterval<IT>> inImgs,
	             final Img<LT> markerImg);
}
