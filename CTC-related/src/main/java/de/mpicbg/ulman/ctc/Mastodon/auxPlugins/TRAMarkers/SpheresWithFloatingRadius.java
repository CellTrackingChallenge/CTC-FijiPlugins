package de.mpicbg.ulman.ctc.Mastodon.auxPlugins.TRAMarkers;

import net.imglib2.util.Util;
import net.imglib2.RealLocalizable;
import de.mpicbg.ulman.ctc.Mastodon.auxPlugins.TRAMarkersProvider;

public class SpheresWithFloatingRadius implements TRAMarkersProvider.intersectionDecidable
{
	@Override
	public void setHalfBBoxInterval(final double[] halfBBoxSize, final double radius)
	{
		halfBBoxSize[0] = radius;
		halfBBoxSize[1] = radius;
		halfBBoxSize[2] = radius;
	}

	@Override
	public boolean isInside(final double[] distVec, final double radius)
	{
		final double lenSq = (distVec[0] * distVec[0]) + (distVec[1] * distVec[1]) + (distVec[2] * distVec[2]);
		return lenSq <= (radius*radius);
	}

	@Override
	public String printInfo()
	{
		return "Sphere with radius decided by each spot individually";
	}
}
