package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.util.XorShift1024StarRandom;

import java.util.Random;

import org.junit.Test;

public class Rank11Test extends RankSelectTestCase {

	@Test
	public void testEmpty() {
		Rank11 rank11b;
		rank11b = new Rank11( new long[ 1 ], 64 );
		assertRank( rank11b );
		rank11b = new Rank11( new long[ 2 ], 128 );
		assertRank( rank11b );
		rank11b = new Rank11( new long[ 1 ], 63 );
		assertRank( rank11b );
		rank11b = new Rank11( new long[ 2 ], 65 );
		assertRank( rank11b );
		rank11b = new Rank11( new long[ 3 ], 129 );
		assertRank( rank11b );
	}

	@Test
	public void testSingleton() {
		Rank11 rank11b;

		rank11b = new Rank11( new long[] { 1L << 63, 0 }, 64 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 1 }, 64 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 1L << 63, 0 }, 128 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 1L << 63, 0 }, 65 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 1L << 63, 0, 0 }, 129 );
		assertRank( rank11b );
	}

	@Test
	public void testDoubleton() {
		Rank11 rank11b;

		rank11b = new Rank11( new long[] { 1 | 1L << 32 }, 64 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 1, 1 }, 128 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 1 | 1L << 32, 0 }, 63 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 1, 1, 0 }, 129 );
		assertRank( rank11b );
	}

	@Test
	public void testAlternating() {
		Rank11 rank11b;

		rank11b = new Rank11( new long[] { 0xAAAAAAAAAAAAAAAAL }, 64 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 0xAAAAAAAAL }, 33 );
		assertRank( rank11b );

		rank11b = new Rank11( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128 );
		assertRank( rank11b );
	}

	@Test
	public void testSelect() {
		Rank11 rank11b;
		rank11b = new Rank11( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ).bits(), 7 );
		assertRank( rank11b );
	}

	@Test
	public void testRandom() {
		for ( int size = 10; size <= 100000000; size *= 10 ) {
			System.err.println( size );
			Random r = new XorShift1024StarRandom( 1 );
			LongArrayBitVector bitVector = LongArrayBitVector.getInstance( size );
			for ( int i = 0; i < size; i++ )
				bitVector.add( r.nextBoolean() );
			Rank11 rank11b;

			rank11b = new Rank11( bitVector );
			assertRank( rank11b );
		}
	}

	@Test
	public void testAllSizes() {
		LongArrayBitVector v;
		Rank11 rank11b;
		for ( int size = 0; size <= 4096; size++ ) {
			v = LongArrayBitVector.getInstance().length( size );
			for ( int i = ( size + 1 ) / 2; i-- != 0; )
				v.set( i * 2 );
			rank11b = new Rank11( v );
			assertRank( rank11b );
		}
	}
}
