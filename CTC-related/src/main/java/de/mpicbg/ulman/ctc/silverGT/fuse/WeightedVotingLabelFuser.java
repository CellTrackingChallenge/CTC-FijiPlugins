package de.mpicbg.ulman.ctc.silverGT.fuse;

import net.imglib2.type.numeric.RealType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import java.util.Vector;
import de.mpicbg.ulman.ctc.silverGT.extract.LabelExtractor;

public class WeightedVotingLabelFuser<IT extends RealType<IT>, ET extends RealType<ET>>
implements LabelFuser<IT,ET>
{
	/** convenience (alias) method to set the this.minFractionOfMarker attribute */
	public
	void setMinAcceptableWeight(final double minAcceptableWeight)
	{
		this.minAcceptableWeight = minAcceptableWeight;
	}

	public
	double minAcceptableWeight = 0.01f;

	/**
	 * Input images are cummulated into "a certainty" how strongly a given
	 * voxel should appear in the final fused segment. The output image is
	 * then thresholded with this.minAcceptableWeight, which one should set
	 * beforehand with, e.g., setMinAcceptableWeight(), and is made binary.
	 */
	@Override
	public
	void fuseMatchingLabels(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                        final Vector<Float> inLabels,
	                        final LabelExtractor<IT,?,ET> le,
	                        final Vector<Double> inWeights,
	                        final Img<ET> outImg)
	{
		//the "adding constant" with the weight of an image
		final ET ONE = outImg.firstElement().createVariable();

		for (int i=0; i < inImgs.size(); ++i)
		{
			if (inImgs.get(i) == null) continue;

			//change the "adding constant" to the weight of this image...
			ONE.setReal(inWeights.get(i));
			//...and extract this label into a temporary image
			le.addGivenLabel(inImgs.get(i),inLabels.get(i), outImg,ONE);
		}

		//finalize the current fused segment
		LoopBuilder.setImages(outImg).forEachPixel(
			(a) -> a.setReal( a.getRealFloat() >= minAcceptableWeight ? 1 : 0 ) );
	}
}
