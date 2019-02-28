package de.mpicbg.ulman.Mastodon;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.Cursor;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class testParallelSaving
{
	public static void main(String... args)
	{
		final ParallelImgSaver saver = new ParallelImgSaver(2);
		System.out.println("main(): all threads are started");

		for (int cnt = 0; cnt < 10; ++cnt)
		{
			final String outImgFilename = String.format("/tmp/test%03d.tif", cnt);

			try {
				System.out.println("main(): adding image no. "+cnt);
				saver.addImgSaveRequestOrBlockUntilLessThan(5, (Img)imgGenerator(cnt),outImgFilename);
				System.out.println("main(): added  image no. "+cnt);
			}
			catch (InterruptedException e) {
				System.out.println("main(): got interrupted.");
				e.printStackTrace();
			}
		}

		System.out.println("main(): waiting for threads to finish");
		try {
			saver.closeAllWorkers_FinishFirstAllUnsavedImages();
		} catch (InterruptedException e) {
			System.out.println("main(): threads-closing got interrupted.");
			e.printStackTrace();
		}
	}

	static <NT extends NumericType<NT>>
	Img<NT> imgGenerator(final int ID)
	{
		return fillImg( new ArrayImgFactory(new UnsignedShortType()).create(2000,1000,5), ID );
	}

	static <RT extends RealType<RT>>
	Img<RT> fillImg(final Img<RT> img, final int ID)
	{
		Cursor<RT> imgC = img.cursor();

		//imprint the "signature"
		imgC.next().setReal(ID);

		//fill the rest with a "random pattern"
		long counter = 100;
		while (imgC.hasNext())
			imgC.next().setReal(counter++);

		return img;
	}
}
