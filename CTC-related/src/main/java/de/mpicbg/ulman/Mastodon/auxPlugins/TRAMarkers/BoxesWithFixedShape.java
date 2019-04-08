package de.mpicbg.ulman.Mastodon.auxPlugins.TRAMarkers;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imglib2.RealLocalizable;
import de.mpicbg.ulman.Mastodon.auxPlugins.TRAMarkersProvider;

@Plugin( type = BoxesWithFixedShape.class, visible = false,
         name = "Specify the full box size in pixels:" )
public class BoxesWithFixedShape implements TRAMarkersProvider.intersectionDecidable, Command
{
	@Parameter(min = "0", stepSize = "1")
	int xFullWidth = 0;

	@Parameter(min = "0", stepSize = "1")
	int yFullWidth = 0;

	@Parameter(min = "0", stepSize = "1")
	int zFullWidth = 0;

	//shortcut: pixel width of half of the box
	double xHalfSize, yHalfSize, zHalfSize;

	@Override
	public void init()
	{
		xHalfSize = (double)xFullWidth/2.0;
		yHalfSize = (double)yFullWidth/2.0;
		zHalfSize = (double)zFullWidth/2.0;
	}

	@Override
	public void setHalfBBoxInterval(final double[] halfBBoxSize, final double radius)
	{
		halfBBoxSize[0] = xHalfSize;
		halfBBoxSize[1] = yHalfSize;
		halfBBoxSize[2] = zHalfSize;
	}

	@Override
	public boolean isInside(final RealLocalizable pos, final RealLocalizable centre, final double radius)
	{
		if (Math.abs(pos.getFloatPosition(0)-centre.getFloatPosition(0)) > xHalfSize) return false;
		if (Math.abs(pos.getFloatPosition(1)-centre.getFloatPosition(1)) > yHalfSize) return false;
		if (Math.abs(pos.getFloatPosition(2)-centre.getFloatPosition(2)) > zHalfSize) return false;
		return true;
	}

	@Override
	public String printInfo()
	{
		return "Box with fixed shape of "+xFullWidth+" x "+yFullWidth+" x "+zFullWidth+" px";
	}

	@Override
	public void run() { /* intentionally empty */ }
}
