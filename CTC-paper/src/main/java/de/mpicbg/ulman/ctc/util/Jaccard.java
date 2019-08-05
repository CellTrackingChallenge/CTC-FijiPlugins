package de.mpicbg.ulman.ctc.util;

import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;

public class Jaccard {
	/** label-free, aka binary, variant of the Jaccard similarity coefficient */
	static public < T extends RealType<T>>
	double Jaccard( final RandomAccessibleInterval<T> imgA, final RandomAccessibleInterval<T> imgB )
	{
		//sanity checks first: images must be of the same size
		if (imgA.numDimensions() != imgB.numDimensions())
			throw new IllegalArgumentException("Both input images have to be of the same dimension.");

		final int n = imgA.numDimensions();
		int i = 0;
		while ( i < n && imgA.dimension(i) ==  imgB.dimension(i) ) ++i;
		if (i < n)
			throw new IllegalArgumentException("Images differ in size in dimension "+i+".");

		//Jaccard is size of an intersection over size of an union,
		//and we consider binary images...
		long interSize = 0;
		long unionSize = 0;

		//sweeping stuff
		final Cursor<T> cA = Views.iterable(imgA).localizingCursor();
		final RandomAccess<T> cB = imgB.randomAccess();

		while (cA.hasNext())
		{
			final boolean isFGA = cA.next().getRealFloat() > 0;

			cB.setPosition( cA );
			final boolean isFGB = cB.get().getRealFloat() > 0;

			interSize += isFGA && isFGB ? 1 : 0;
			unionSize += isFGA || isFGB ? 1 : 0;
		}

		return ( (double)interSize / (double)unionSize );
	}


	static public < T extends IntegerType<T>>
	double Jaccard( final RandomAccessibleInterval<T> imgA, final int labelA,
	                final RandomAccessibleInterval<T> imgB, final int labelB )
	{
		//sanity checks first: images must be of the same size
		if (imgA.numDimensions() != imgB.numDimensions())
			throw new IllegalArgumentException("Both input images have to be of the same dimension.");

		final int n = imgA.numDimensions();
		int i = 0;
		while ( i < n && imgA.dimension(i) ==  imgB.dimension(i) ) ++i;
		if (i < n)
			throw new IllegalArgumentException("Images differ in size in dimension "+i+".");

		//Jaccard is size of an intersection over size of an union,
		//and we consider binary images...
		long interSize = 0;
		long unionSize = 0;

		//sweeping stuff
		final Cursor<T> cA = Views.iterable(imgA).localizingCursor();
		final RandomAccess<T> cB = imgB.randomAccess();

		while (cA.hasNext())
		{
			final boolean isFGA = cA.next().getInteger() == labelA;

			cB.setPosition( cA );
			final boolean isFGB = cB.get().getInteger() == labelB;

			interSize += isFGA && isFGB ? 1 : 0;
			unionSize += isFGA || isFGB ? 1 : 0;
		}

		return ( (double)interSize / (double)unionSize );
	}
}
