package de.mpicbg.ulman.ctc.silverGT.postprocess;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;

public interface LabelPostprocessor<LT extends IntegerType<LT>>
{
	/**
	 * Processes the label/segment with voxel values 'markerValue' in
	 * the input-output image 'img'.
	 */
	void processLabel(final Img<LT> img,
	                  final int markerValue);
}
