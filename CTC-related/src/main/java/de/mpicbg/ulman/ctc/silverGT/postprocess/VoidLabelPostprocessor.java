package de.mpicbg.ulman.ctc.silverGT.postprocess;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;

public class VoidLabelPostprocessor<LT extends IntegerType<LT>>
implements LabelPostprocessor<LT>
{
	/** does nothing at all */
	@Override
	public
	void processLabel(final Img<LT> img,
	                  final int markerValue)
	{}
}
