package de.mpicbg.ulman.ctc.Mastodon.auxPlugins.TRAMarkers;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpicbg.ulman.ctc.Mastodon.auxPlugins.TRAMarkersProvider;

@Plugin( type = BoxesWithFixedShape.class, visible = false,
         name = "Specify the full box size in the image units (e.g. in microns):" )
public class BoxesWithFixedShape implements TRAMarkersProvider.intersectionDecidable, Command
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
	double xFullWidth = 0;

	@Parameter(min = "0", stepSize = "1")
	double yFullWidth = 0;

	@Parameter(min = "0", stepSize = "1")
	double zFullWidth = 0;

	//shortcut: width of half of the box
	double xHalfSize, yHalfSize, zHalfSize;

	@Override
	public void init()
	{
		xHalfSize = xFullWidth/2.0;
		yHalfSize = yFullWidth/2.0;
		zHalfSize = zFullWidth/2.0;
	}

	@Override
	public void setHalfBBoxInterval(final double[] halfBBoxSize, final double radius)
	{
		halfBBoxSize[0] = xHalfSize;
		halfBBoxSize[1] = yHalfSize;
		halfBBoxSize[2] = zHalfSize;
	}

	@Override
	public boolean isInside(final double[] distVec, final double radius)
	{
		if (Math.abs(distVec[0]) > xHalfSize) return false;
		if (Math.abs(distVec[1]) > yHalfSize) return false;
		if (Math.abs(distVec[2]) > zHalfSize) return false;

		//to prevent the full-even-sized boxes to have +1 size
		//(e.g., fullWidth=4 -> halfSize=2 -> would create 2+1+2 wide box)
		if (distVec[0] == xHalfSize) return false;
		if (distVec[1] == yHalfSize) return false;
		if (distVec[2] == zHalfSize) return false;
		return true;
	}

	@Override
	public String printInfo()
	{
		return "Box with fixed shape of "+xFullWidth+" x "+yFullWidth+" x "+zFullWidth;
	}

	@Override
	public void run() { /* intentionally empty */ }
}
