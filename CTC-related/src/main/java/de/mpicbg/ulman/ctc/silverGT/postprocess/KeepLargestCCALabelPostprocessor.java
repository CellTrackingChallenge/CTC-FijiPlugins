package de.mpicbg.ulman.ctc.silverGT.postprocess;

import net.imglib2.type.numeric.IntegerType;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.img.Img;
import net.imglib2.view.Views;
import net.imglib2.view.IntervalView;
import net.imglib2.algorithm.labeling.ConnectedComponents;

import java.util.HashMap;
import de.mpicbg.ulman.ctc.silverGT.extract.MajorityOverlapBasedLabelExtractor;

/**
 * @author Vladimír Ulman
 * @author Cem Emre Akbas
 */
public class KeepLargestCCALabelPostprocessor<LT extends IntegerType<LT>>
implements LabelPostprocessor<LT>
{
	private Img<LT> ccaInImg  = null; //copy of just one label
	private Img<LT> ccaOutImg = null; //result of CCA on this one label
	private long[] minBound = null, maxBound = null;

	protected
	void resetAuxAttribs(final Img<LT> templateImg)
	{
		ccaInImg  = templateImg.factory().create(templateImg);
		ccaOutImg = templateImg.factory().create(templateImg);
		minBound = new long[templateImg.numDimensions()];
		maxBound = new long[templateImg.numDimensions()];
	}


	@Override
	public
	void processLabel(final Img<LT> img,
	                  final int markerValue)
	{
		//TODO: should change everytime the img is not compatible with any of the aux attribs
		if (ccaInImg == null || minBound == null) resetAuxAttribs(img);

		//localize the marker in the processed image
		final Cursor<LT> markerCursor = img.localizingCursor();
		while (markerCursor.hasNext() && markerCursor.next().getInteger() != markerValue) ;

		//determine marker's size and the AABB it spans
		MajorityOverlapBasedLabelExtractor.findAABB(markerCursor, minBound,maxBound);
		final Interval ccaInterval = new FinalInterval(minBound, maxBound); //TODO: can't I be reusing the same Interval?

		//copy out only this marker
		final IntervalView<LT> ccaInView = Views.interval(ccaInImg,ccaInterval);
		      Cursor<LT> ccaCursor = ccaInView.cursor();
		final Cursor<LT> outCursor = Views.interval(img,ccaInterval).cursor();
		while (ccaCursor.hasNext())
		{
			ccaCursor.next().setInteger( outCursor.next().getInteger() == markerValue ? 1 : 0 );
		}

		//CCA to this View
		final IntervalView<LT> ccaOutView = Views.interval(ccaOutImg,ccaInterval);
		//since the View comes from one shared large image, there might be results of CCA for other markers,
		//we better clear it before (so that the CCA function cannot be fooled by some previous result)
		ccaCursor = ccaOutView.cursor();
		while (ccaCursor.hasNext()) ccaCursor.next().setZero();

		final int noOfLabels
			= ConnectedComponents.labelAllConnectedComponents(ccaInView,ccaOutView, ConnectedComponents.StructuringElement.EIGHT_CONNECTED);

		//is there anything to change?
		if (noOfLabels > 1)
		{
			System.out.println("CCA for marker "+markerValue+": choosing one from "+noOfLabels+" components");

			//calculate sizes of the detected labels
			final HashMap<Integer,Integer> hist = new HashMap<>(10);
			ccaCursor.reset();
			while (ccaCursor.hasNext())
			{
				final int curLabel = ccaCursor.next().getInteger();
				if (curLabel == 0) continue; //skip over the background component
				final Integer count = hist.get(curLabel);
				hist.put(curLabel, count == null ? 1 : count+1 );
			}

			//find the most frequent pixel value (the largest label)
			int largestCC = -1;
			int largestSize = 0;
			int totalSize = 0;
			for (Integer lab : hist.keySet())
			{
				final int size = hist.get(lab);
				if (size > largestSize)
				{
					largestSize = size;
					largestCC   = lab;
				}
				totalSize += size;
			}
			System.out.println("CCA for marker "+markerValue+": chosen component no. "+largestCC+" which constitutes "
									 +(float)largestSize/(float)totalSize+" % of the original size");

			//remove anything from the current marker that does not overlap with the largest CCA component
			ccaCursor.reset();
			outCursor.reset();
			while (ccaCursor.hasNext())
			{
				ccaCursor.next();
				if ( outCursor.next().getInteger() == markerValue && ccaCursor.get().getInteger() != largestCC )
					outCursor.get().setZero();
			}
		}
	}
}
