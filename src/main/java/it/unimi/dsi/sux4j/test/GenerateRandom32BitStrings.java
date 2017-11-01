package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

public class GenerateRandom32BitStrings {
	public static final Logger LOGGER = LoggerFactory.getLogger( GenerateRandom32BitStrings.class );
	
	public static void main( final String[] arg ) throws JSAPException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP( GenerateRandom32BitStrings.class.getName(), "Generates a list of sorted 32-bit random strings using only characters in the ISO-8859-1 printable range [32..256).",
				new Parameter[] {
					new FlaggedOption( "gap", JSAP.INTSIZE_PARSER, "1", JSAP.NOT_REQUIRED, 'g', "gap", "Impose a minimum gap." ),
					new UnflaggedOption( "n", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The number of strings (too small values might cause overflow)." ),
					new UnflaggedOption( "output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The output file." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final int n = jsapResult.getInt( "n" );
		final String output = jsapResult.getString( "output" );
		final int gap = jsapResult.getInt( "gap" );
		
		RandomGenerator r = new XorShift1024StarRandomGenerator();
	
		ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.expectedUpdates = n;
		pl.start( "Generating... " );
		
		double l = 0, t;
		double limit = Math.pow( 224, 4 );
		int incr = (int)Math.floor( 1.99 * ( limit / n ) ) - 1;
		
		LOGGER.info( "Increment: " + incr );
		
		@SuppressWarnings("resource")
		final FastBufferedOutputStream fbs = new FastBufferedOutputStream( new FileOutputStream( output ) );
		int[] b = new int[ 4 ];

		for( int i = 0; i < n; i++ ) {
			t = ( l += ( r.nextInt( incr ) + gap ) );
			if ( l >= limit ) throw new AssertionError( Integer.toString( i ) );
			for( int j = 4; j-- != 0; ) {
				b[ j ] = (int)( t % 224 + 32 );
				t = Math.floor( t / 224 );
			}
			
			for( int j = 0; j < 4; j++ ) fbs.write( b[ j ] );
			fbs.write( 10 );

			pl.lightUpdate();
		}
		
		
		pl.done();
		fbs.close();
		
		LOGGER.info( "Last/limit: " + ( l / limit ) );
	}
}
