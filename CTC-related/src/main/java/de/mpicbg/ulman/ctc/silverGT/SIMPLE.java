/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2019 Vladimír Ulman
 *
 * @author Vladimír Ulman
 */
package de.mpicbg.ulman.ctc.silverGT;

import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.log.LogService;

import de.mpicbg.ulman.ctc.silverGT.extract.MajorityOverlapBasedLabelExtractor;
import de.mpicbg.ulman.ctc.silverGT.fuse.SIMPLELabelFuser;
import de.mpicbg.ulman.ctc.silverGT.postprocess.KeepLargestCCALabelPostprocessor;
import de.mpicbg.ulman.ctc.silverGT.insert.CollisionsAwareLabelInsertor;

public
class SIMPLE<IT extends RealType<IT>, LT extends IntegerType<LT>>
extends AbstractWeightedVotingFusionAlgorithm<IT,LT>
{
	public
	SIMPLE(final LogService _log)
	{
		super(_log);
	}

	@Override
	protected
	void setFusionComponents()
	{
		//setup the individual stages
		final MajorityOverlapBasedLabelExtractor<IT,LT,DoubleType> e = new MajorityOverlapBasedLabelExtractor<>();
		e.minFractionOfMarker = 0.5f;

		final SIMPLELabelFuser<IT,DoubleType> f = new SIMPLELabelFuser<>();

		final CollisionsAwareLabelInsertor<LT, DoubleType> i = new CollisionsAwareLabelInsertor<>();

		final KeepLargestCCALabelPostprocessor<LT> p = new KeepLargestCCALabelPostprocessor<>();

		this.labelExtractor = e;
		this.labelFuser     = f;
		this.labelInsertor  = i;
		this.labelCleaner   = p;
	}

	private String dbgImgFileName;
}
