package org.twak.tweed.gen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.twak.tweed.Tweed;

public class HeightGen extends Gen {


	public Map<String, OS> heights = new HashMap();

	public static class OS {
		double abshmin, absh2, abshmax, relh2, relhmax;

		public OS( double abshmin, double absh2, double abshmax, double relh2, double relhmax ) {
			this.abshmin = abshmin;
			this.absh2 = absh2;
			this.abshmax = abshmax;
			this.relh2 = relh2;
			this.relhmax = relhmax;
		}

	}

	public HeightGen( File location, Tweed tweed ) {
		super( location.getName(), tweed );

		final int[] count = new int[1];

		for ( File csv : new File( "/home/twak/data/Download_around_ucl_562795" ).listFiles() ) {
			if ( csv.getName().endsWith( ".csv" ) ) {
				System.out.println( "loading to redis " + csv.getName() );

				try ( Stream<String> stream = Files.lines( csv.toPath() ) ) {

					stream.forEach( new Consumer<String>() {
						@Override
						public void accept( String line ) {
							String[] vals = line.split( "," );
							String name = vals[ 1 ];

							try {
							heights.put( name, new OS( Double.parseDouble( vals[ 5 ] ), Double.parseDouble( vals[ 6 ] ), Double.parseDouble( vals[ 7 ] ), Double.parseDouble( vals[ 8 ] ), Double.parseDouble( vals[ 9 ] ) ) );
							}
							catch (NumberFormatException e) {}

							count[ 0 ]++;
						}
					} );

				} catch ( IOException e ) {
					e.printStackTrace();
				}

			}
		}
		System.out.println( count[ 0 ] + " records loaded" );
	}

	@Override
	public JComponent getUI() {
		return new JLabel("no 3d");
	}

	public Map getProperties( String name ) {
		Map<String, Object> out = new HashMap();
		
		OS os = heights.get(name);
		
		if (os != null) {
			out.put( "abshmin", os.abshmin );
			out.put( "absh2", os.absh2 );
			out.put( "abshmax", os.abshmax );
			out.put( "relh2", os.relh2 );
			out.put( "relhmax", os.relhmax );
		}
		
		return out;
	}

}
