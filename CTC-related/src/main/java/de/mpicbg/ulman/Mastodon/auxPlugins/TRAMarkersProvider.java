package de.mpicbg.ulman.Mastodon.auxPlugins;

import net.imglib2.RealLocalizable;

public class TRAMarkersProvider
{
	public interface intersectionDecidable
	{
		default void init() {};

		//void sweepingInterval(final double[] min, final double max[]);
		boolean isInside(final RealLocalizable pos, final RealLocalizable centre, final double radius);
	}


}
