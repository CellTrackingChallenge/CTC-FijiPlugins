package de.mpicbg.ulman.ctc.silverGT.fuse;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import java.util.Vector;
import de.mpicbg.ulman.ctc.silverGT.extract.LabelExtractor;

public class SIMPLELabelFuser<IT extends RealType<IT>, ET extends RealType<ET>>
implements LabelFuser<IT,ET>
{
	/**
	 * Values in the output image are binary: 0 - background, 1 - fused segment.
	 * Note that the output may not necessarily be a single connected component.
	 */
	@Override
	public
	void fuseMatchingLabels(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                        final Vector<Float> inLabels,
	                        final LabelExtractor<IT,?,ET> le,
	                        final Vector<Double> inWeights,
	                        final Img<ET> outImg)
	{
		//da plan:
		// outImg will contain the current candidate fusion segment
		// one would always evaluate this segment against inImgs+inLabels pair,
		//   and adapt the inWeights accordingly
		// inputs that get below the quality threshod will be "erased" by
		//   setting their respective inImgs[i] to null
	}
}
