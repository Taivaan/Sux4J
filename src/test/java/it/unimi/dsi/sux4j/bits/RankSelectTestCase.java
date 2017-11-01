package it.unimi.dsi.sux4j.bits;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.BitVector;

public abstract class RankSelectTestCase {
	public void assertRankAndSelect( Rank rank, Select select ) {
		final long length = rank.bitVector().length();
		final BitVector bits = rank.bitVector();

		for( int j = 0, i = 0; i < length; i++ ) {
			assertEquals( "Ranking " + i, j, rank.rank( i ) );
			if ( bits.getBoolean( i ) ) {
				assertEquals( "Selecting " + j, i, select.select( j ) );
				j++;
			}
			
		}
	}

	public void assertSelect( Select s ) {
		final BitVector bits = s.bitVector();
		final long length = bits.length();
		
		for( int j = 0, i = 0; i < length; i++ ) {
			if ( bits.getBoolean( i ) ) {
				assertEquals( "Selecting " + j, i, s.select( j ) );
				j++;
			}
			
		}
	}

	public void assertSelectZero( SelectZero s ) {
		final BitVector bits = s.bitVector();
		final long length = bits.length();
		
		for( int j = 0, i = 0; i < length; i++ ) {
			if ( ! bits.getBoolean( i ) ) {
				assertEquals( "Selecting " + j, i, s.selectZero( j ) );
				j++;
			}
			
		}
	}

	public void assertRank( Rank rank ) {
		final long length = rank.bitVector().length();
		final BitVector bits = rank.bitVector();
		
		for( long j = 0, i = 0; i < length; i++ ) {
			assertEquals( "Ranking " + i, j, rank.rank( i ) );
			if ( bits.getBoolean( i ) ) j++;
		}
	}

}
