package de.mpicbg.ulman.ctc.silverGT.extract;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;

/**
 * Detects labels in input images (of voxel type IT -- Input Type) that match given marker,
 * which is to be found in the marker image (of voxel type LT -- Label Type); and extracts
 * the input markers into some (possibly) other image (of voxel type ET -- Extract_as Type).
 *
 * @param <IT>  voxel type of input images (with labels to be fused together)
 * @param <LT>  voxel type of the marker/label image (on which the labels are synchronized)
 * @param <ET>  voxel type of the output image (into which the input labels are extracted)
 */
public interface LabelExtractor<IT extends RealType<IT>, LT extends IntegerType<LT>, ET extends RealType<ET>>
{
	/**
	 * Just returns the value of the matching label. Typically this could be the label
	 * that overlaps the 'inMarker' the most (compared to other overlapping markers).
	 *
	 * @param inII          Sweeper of the input image (from which label is to be returned)
	 * @param markerII      Sweeper of the input marker image
	 * @param markerValue   Marker (from the input marker image) in question...
	 */
	float findMatchingLabel(final IterableInterval<IT> inII,
	                        final IterableInterval<LT> markerII,
	                        final int markerValue);

	/**
	 * Just finds pixels of 'wantedLabel' value and sets the corresponding pixels
	 * to 'saveAsLabel' value in the output image.
	 */
	void isolateGivenLabel(final RandomAccessibleInterval<IT> sourceRAI,
	                       final float wantedLabel,
	                       final RandomAccessibleInterval<ET> outputRAI,
	                       final ET saveAsLabel);

	/**
	 * Just finds pixels of 'wantedLabel' value and increases the value of the
	 * corresponding pixels with 'addThisLabel' value in the output image.
	 */
	void addGivenLabel(final RandomAccessibleInterval<IT> sourceRAI,
	                   final float wantedLabel,
	                   final RandomAccessibleInterval<ET> outputRAI,
	                   final ET addThisLabel);
}
