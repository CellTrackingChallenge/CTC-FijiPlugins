package de.mpicbg.ulman.ctc.Mastodon.auxPlugins;

import java.util.concurrent.ExecutionException;
import org.scijava.command.CommandService;

import net.imglib2.RealLocalizable;

import de.mpicbg.ulman.ctc.Mastodon.auxPlugins.TRAMarkers.*;

public class TRAMarkersProvider
{
	public
	interface intersectionDecidable
	{
		default void init() {};

		void setHalfBBoxInterval(final double[] halfBBoxSize, final double radius);
		boolean isInside(final double[] distVec, final double radius);

		default String printInfo() { return toString(); }
	}

	public static final
	String[] availableChoices = {
		"Spheres of spot-driven radii",
		"Spheres of fixed radius",
		"Boxes of fixed shape" };

	public static
	intersectionDecidable TRAMarkerFactory(final String choice, final CommandService cs)
	{
		intersectionDecidable markerShape;

		//sanity branch...
		if (choice == null || cs == null)
		{
			markerShape = new SpheresWithFloatingRadius();
			markerShape.init();
			return markerShape;
		}

		//the main branch where 'choice' param is taken seriously...
		try
		{
			if (choice.startsWith("Boxes"))
				markerShape = (intersectionDecidable)cs.run(BoxesWithFixedShape.class,true).get().getCommand();
			else if (choice.contains("fixed"))
				markerShape = (intersectionDecidable)cs.run(SpheresWithFixedRadius.class,true).get().getCommand();
			else
				markerShape = new SpheresWithFloatingRadius();

			markerShape.init();
		}
		catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
			return null;
		}

		return markerShape;
	}
}
