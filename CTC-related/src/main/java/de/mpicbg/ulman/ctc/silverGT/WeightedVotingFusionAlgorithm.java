package de.mpicbg.ulman.ctc.silverGT;

import net.imglib2.type.numeric.RealType;
import java.util.Vector;

/**
 * Specialized variant of a fusion algorithm that requires weight per every input image,
 * and a threshold to define the minimal necessary cumulation of weights to include a voxel
 * into the fused output. The implementation should follow this pattern:
 *
 * O[x] = SUM_i=indicesOfInputImages w(i)*I(i)[x] >= T ? 1 : 0
 *
 * where
 * O[x] is the indicator (binary) image output value at position 'x',
 * I(i)[x] is the i-th indicator image input value at position 'x',
 * w(i) is the weight associated with the i-th input image, and
 * T is the said minimal necessary cumulation of weights.
 */
public
interface WeightedVotingFusionAlgorithm <IT extends RealType<IT>, LT extends RealType<LT>>
extends FusionAlgorithm<IT,LT>
{
	/** Set weights associated with the input images, needless to say that the length
	    of this collection must match the length of the collection with input images. */
	void setWeights(final Vector<Double> weights);

	/** Sets the minimal necessary cumulation of weights to include a voxel into the output. */
	void setThreshold(final double minSumOfWeights);
}
