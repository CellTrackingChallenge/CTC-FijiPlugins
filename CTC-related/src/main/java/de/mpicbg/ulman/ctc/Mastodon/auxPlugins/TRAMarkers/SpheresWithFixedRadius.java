package de.mpicbg.ulman.ctc.Mastodon.auxPlugins.TRAMarkers;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpicbg.ulman.ctc.Mastodon.auxPlugins.TRAMarkersProvider;

@Plugin( type = SpheresWithFixedRadius.class, visible = false,
         name = "Specify the sphere radius in the image units (e.g. in microns):" )
public class SpheresWithFixedRadius implements TRAMarkersProvider.intersectionDecidable, Command
{
	@Parameter(visibility = ItemVisibility.MESSAGE, required = false, initializer = "setResHint")
	String resolutionMsg = "Unknown resolution of the image";
	//
	void setResHint()
	{ resolutionMsg = resolutionHint; }
	//
	@Parameter(visibility = ItemVisibility.INVISIBLE, required = false)
	String resolutionHint = "Unknown resolution of the image";

	@Parameter(min = "0", stepSize = "1")
	double fixedRadius = 0;

	//shortcut
	double fixedRadiusSq;

	@Override
	public void init()
	{
		fixedRadiusSq = fixedRadius*fixedRadius;
	}

	@Override
	public void setHalfBBoxInterval(final double[] halfBBoxSize, final double radius)
	{
		halfBBoxSize[0] = fixedRadius;
		halfBBoxSize[1] = fixedRadius;
		halfBBoxSize[2] = fixedRadius;
	}

	@Override
	public boolean isInside(final double[] distVec, final double radius)
	{
		final double lenSq = (distVec[0] * distVec[0]) + (distVec[1] * distVec[1]) + (distVec[2] * distVec[2]);
		return lenSq <= fixedRadiusSq;
	}

	@Override
	public String printInfo()
	{
		return "Sphere with fixed radius of "+fixedRadius;
	}

	@Override
	public void run() { /* intentionally empty */ }
}
